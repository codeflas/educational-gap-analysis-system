package ie.ul.egas.gapanalysis.infrastructure.persistence;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.GapFixtures;
import ie.ul.egas.gapanalysis.api.SkillGapId;
import ie.ul.egas.gapanalysis.domain.GapReportRepository;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.domain.model.GapReportSummary;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.gapanalysis.domain.policy.OrdinalDistanceSeverityPolicy;
import ie.ul.egas.learner.api.LearnerId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The gap-report adapter against real PostgreSQL: round-trip fidelity, the two invariants V401
 * enforces at rest, and the cost of reading.
 *
 * <p>The assertions that matter most are the two ADR-021 properties that a storage layer is most
 * likely to quietly destroy. <b>Absence must survive</b> — an unassessed finding has to come back
 * holding no attainment rather than a zero, since a schema that stored a sentinel would collapse the
 * distinction on the way in and nothing downstream could recover it. And <b>a stored judgement must
 * be restored, not recomputed</b> — a reload that re-ran the severity policy would silently restate
 * a historical report under whatever rule is configured today.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaGapReportRepository.class})
class JpaGapReportRepositoryTests {

    private static final GapSeverityPolicy POLICY = new OrdinalDistanceSeverityPolicy();

    @Autowired
    GapReportRepository repository;

    @Autowired
    JdbcClient jdbc;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void roundTripsTheWholeExplainabilityChain() {
        // target -> attainment -> evidence provenance, every link asserted after a reload: this is
        // the data RQ3's explainability claim rests on, so its survival is the point of the table.
        SkillGap gap = SkillGap.assess(
                GapFixtures.target(3),
                GapFixtures.attainment(1, List.of(
                        new EvidenceSnapshot("SELF_DECLARED", 1, "L1", 0.4,
                                "Self-assessment, March 2026", GapFixtures.NOW),
                        new EvidenceSnapshot("CERTIFICATION", 2, "L2", 0.95,
                                "ISTQB Foundation, 2025", GapFixtures.NOW.minusSeconds(3600)))),
                POLICY);
        GapReport written = reportWith(gap);

        GapReport read = saveAndReload(written);

        assertThat(read.id()).isEqualTo(written.id());
        assertThat(read.learnerId()).isEqualTo(GapFixtures.LEARNER);
        assertThat(read.frameworkId()).isEqualTo(GapFixtures.FRAMEWORK);
        assertThat(read.generatedAt()).isEqualTo(GapFixtures.NOW);

        SkillGap reloaded = read.gapFor(GapFixtures.SOFTWARE_DESIGN).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(gap.id());
        assertThat(reloaded.target())
                .usingRecursiveComparison().isEqualTo(gap.target());
        assertThat(reloaded.attainment()).isPresent()
                .get().usingRecursiveComparison().isEqualTo(gap.attainment().orElseThrow());
        assertThat(reloaded.evidence())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(gap.evidence());
        assertThat(reloaded.shortfall()).hasValue(2);
    }

    @Test
    void anUnassessedFindingReloadsAsAbsentRatherThanAsZero() {
        // ADR-021's absent-attainment rule, at rest. A schema storing a zero-ordinal sentinel would
        // make "never assessed" indistinguishable from "assessed at the lowest level" the moment the
        // report was written, and no consumer could tell them apart afterwards.
        SkillGap unassessed = SkillGap.unassessed(GapFixtures.target(3), POLICY);

        GapReport read = saveAndReload(reportWith(unassessed));
        SkillGap reloaded = read.gapFor(GapFixtures.SOFTWARE_DESIGN).orElseThrow();

        assertThat(reloaded.isUnassessed()).isTrue();
        assertThat(reloaded.attainment()).isEmpty();
        assertThat(reloaded.shortfall()).isEmpty();
        assertThat(reloaded.evidence()).isEmpty();
        assertThat(reloaded.severity()).isEqualTo(GapSeverity.UNASSESSED);
        assertThat(read.unassessedGaps()).containsExactly(reloaded);

        // All three attainment columns null, not defaulted — absence written as absence.
        assertThat(jdbc.sql("""
                select count(*) from gap_analysis.skill_gap
                where attained_ordinal is null
                  and attained_level_code is null
                  and attainment_resolved_at is null
                """).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void restoresTheStoredSeverityRatherThanRecomputingItOnLoad() {
        // MET on a finding three levels short: no policy in the codebase would produce this, so if
        // it survives the round trip the adapter cannot be re-deriving severity on read. Re-running
        // a policy on load would rewrite every historical judgement whenever the rule changed.
        SkillGap stored = SkillGap.reconstitute(SkillGapId.random(), GapFixtures.target(3),
                GapFixtures.attainment(0), GapSeverity.MET);

        GapReport read = saveAndReload(reportWith(stored));

        assertThat(read.gapFor(GapFixtures.SOFTWARE_DESIGN).orElseThrow().severity())
                .isEqualTo(GapSeverity.MET);
        assertThat(POLICY.severityFor(GapFixtures.target(3), GapFixtures.attainment(0)))
                .as("the default policy disagrees, which is what makes the assertion above meaningful")
                .isEqualTo(GapSeverity.MAJOR);
    }

    @Test
    void storesSeverityByNameSoAReorderedEnumCannotRewriteHistory() {
        saveAndReload(reportWith(SkillGap.unassessed(GapFixtures.target(2), POLICY)));

        assertThat(jdbc.sql("select severity from gap_analysis.skill_gap")
                .query(String.class).single()).isEqualTo("UNASSESSED");
    }

    @Test
    void keepsEvidenceInTheOrderItWasCopiedIn() {
        // recorded_at is not unique and cannot carry the order, so an explicit sequence does. All
        // three observations share an instant here precisely to prove the sequence is what orders
        // them rather than the timestamp.
        List<EvidenceSnapshot> evidence = List.of(
                GapFixtures.evidence(3, 0.3),
                GapFixtures.evidence(1, 0.6),
                GapFixtures.evidence(2, 0.9));
        SkillGap gap = SkillGap.assess(GapFixtures.target(3),
                GapFixtures.attainment(2, evidence), POLICY);

        GapReport read = saveAndReload(reportWith(gap));

        assertThat(read.gapFor(GapFixtures.SOFTWARE_DESIGN).orElseThrow().evidence())
                .extracting(EvidenceSnapshot::claimedOrdinal)
                .containsExactly(3, 1, 2);
    }

    @Test
    void storesAnObservationThatCarriesNoSource() {
        // EvidenceSnapshot admits a null source where the learner-side record does not; refusing the
        // observation would discard the rest of its provenance over one missing field.
        SkillGap gap = SkillGap.assess(GapFixtures.target(2),
                GapFixtures.attainment(1, List.of(new EvidenceSnapshot(
                        "SELF_DECLARED", 1, "L1", 0.5, null, GapFixtures.NOW))),
                POLICY);

        GapReport read = saveAndReload(reportWith(gap));

        assertThat(read.gapFor(GapFixtures.SOFTWARE_DESIGN).orElseThrow().evidence())
                .singleElement()
                .extracting(EvidenceSnapshot::source).isNull();
    }

    @Test
    void aReportWithNoFindingsRoundTrips() {
        // A framework with no competencies, or an analysis scoped to none, is a legitimate outcome
        // and must not become an unrepresentable one at the storage tier.
        GapReport written = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(), GapFixtures.FIXED_CLOCK);

        GapReport read = saveAndReload(written);

        assertThat(read.id()).isEqualTo(written.id());
        assertThat(read.gaps()).isEmpty();
    }

    @Test
    void aReportEqualsItselfAcrossARoundTripDespiteNanosecondInput() {
        // Java instants carry nanoseconds; timestamptz stores microseconds and PostgreSQL ROUNDS.
        // Untruncated, 09:00:00.0000005 would read back as .000001 and the report would not equal
        // itself. The adapter truncates, so the loss is a stated decision rather than a surprise.
        Instant precise = Instant.parse("2026-08-04T09:00:00Z").plusNanos(500);
        Clock preciseClock = Clock.fixed(precise, ZoneOffset.UTC);

        SkillGap gap = SkillGap.assess(GapFixtures.target(2),
                new AttainmentSnapshot(1, "L1", precise,
                        List.of(new EvidenceSnapshot("SELF_DECLARED", 1, "L1", 0.5, "src", precise))),
                POLICY);
        GapReport written = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(gap), preciseClock);

        GapReport read = saveAndReload(written);
        SkillGap reloaded = read.gapFor(GapFixtures.SOFTWARE_DESIGN).orElseThrow();

        Instant truncated = Instant.parse("2026-08-04T09:00:00Z");
        assertThat(read.generatedAt()).isEqualTo(truncated);
        assertThat(reloaded.attainment().orElseThrow().resolvedAt()).isEqualTo(truncated);
        assertThat(reloaded.evidence().get(0).recordedAt()).isEqualTo(truncated);
    }

    @Test
    void reSavingTheSameReportUpdatesInPlaceRatherThanColliding() {
        // Evidence rows have no domain identity, so their ids are derived from (skill_gap_id, seq).
        // Random ids would insert a replacement set that collided with uq_gap_evidence_skill_gap_seq
        // — the failure the learner adapter met in Step 4 and resolved the same way.
        GapReport report = reportWith(SkillGap.assess(GapFixtures.target(3),
                GapFixtures.attainment(1, List.of(
                        GapFixtures.evidence(1, 0.4), GapFixtures.evidence(2, 0.8))),
                POLICY));

        repository.save(report);
        entityManager.flush();
        entityManager.clear();
        repository.save(report);
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("gap_report")).isEqualTo(1);
        assertThat(countRows("skill_gap")).isEqualTo(1);
        assertThat(countRows("gap_evidence")).isEqualTo(2);
        assertThat(repository.findById(report.id()).orElseThrow().gaps()).hasSize(1);
    }

    @Test
    void theDatabaseRefusesTwoFindingsForTheSameCompetencyInOneReport() {
        // GapReport enforces this in generate() and reconstitute() alike, so the only way to reach
        // the constraint is to go around the aggregate. That it holds here is what turns the
        // aggregate's check from a guard against a possible state into a guarantee the state cannot
        // exist — the role uq_assertion_profile_competency plays for LearnerProfile (ADR-020).
        GapReport report = reportWith(SkillGap.assess(GapFixtures.target(3),
                GapFixtures.attainment(1), POLICY));
        repository.save(report);
        entityManager.flush();

        assertThatThrownBy(() -> insertRawGap(report.id(), GapFixtures.SOFTWARE_DESIGN.value(),
                "null, null, null"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_skill_gap_report_competency");
    }

    @Test
    void theDatabaseRefusesAHalfWrittenAttainment() {
        // Attainment is all-or-nothing: an ordinal with no level code maps to no state the domain
        // can represent, since AttainmentSnapshot requires all three together.
        GapReport report = reportWith(SkillGap.unassessed(GapFixtures.target(3), POLICY));
        repository.save(report);
        entityManager.flush();

        assertThatThrownBy(() -> insertRawGap(report.id(), UUID.randomUUID(), "2, null, null"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_skill_gap_attainment_complete");
    }

    @Test
    void listsOneLearnersReportsNewestFirstWithoutLoadingAnyFinding() {
        LearnerId other = LearnerId.random();
        save(report(GapFixtures.LEARNER, GapFixtures.FRAMEWORK, "2026-03-01T09:00:00Z", 2));
        save(report(GapFixtures.LEARNER, GapFixtures.FRAMEWORK, "2026-06-01T09:00:00Z", 1));
        save(report(other, GapFixtures.FRAMEWORK, "2026-07-01T09:00:00Z", 1));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.clear();

        List<GapReportSummary> summaries = repository.findSummariesForLearner(GapFixtures.LEARNER);

        assertThat(summaries).extracting(GapReportSummary::generatedAt)
                .as("newest first, and the other learner's report is not among them")
                .containsExactly(Instant.parse("2026-06-01T09:00:00Z"),
                        Instant.parse("2026-03-01T09:00:00Z"));
        assertThat(summaries).extracting(GapReportSummary::gapCount).containsExactly(1, 2);
        assertThat(summaries).extracting(GapReportSummary::frameworkId)
                .containsOnly(GapFixtures.FRAMEWORK);
        assertThat(statistics.getEntityLoadCount())
                .as("a listing is metadata plus a count; no finding and no observation is hydrated")
                .isZero();

        assertThat(repository.findSummariesForLearner(LearnerId.random())).isEmpty();
    }

    @Test
    void readingAReportCostsAFixedNumberOfQueries() {
        // The entity graph fetches findings and their evidence with the report. The count must not
        // grow with the number of findings, which is the N+1 that lazy collections would produce.
        GapReport report = report(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                "2026-05-01T09:00:00Z", 8);
        save(report);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.clear();

        GapReport read = repository.findById(report.id()).orElseThrow();
        read.gaps().forEach(gap -> gap.evidence().size());

        assertThat(read.gaps()).hasSize(8);
        assertThat(statistics.getPrepareStatementCount())
                .as("one query for the whole graph, not one per finding")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void reportsAbsenceForAnUnknownIdentifier() {
        assertThat(repository.findById(GapReportId.random())).isEmpty();
    }

    // --- helpers -------------------------------------------------------------------------------

    private GapReport reportWith(SkillGap... gaps) {
        return GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK, List.of(gaps),
                GapFixtures.FIXED_CLOCK);
    }

    /** A report for the given learner at the given instant, carrying {@code gapCount} findings. */
    private GapReport report(LearnerId learnerId, CompetencyFrameworkId frameworkId,
                             String generatedAt, int gapCount) {
        List<SkillGap> gaps = IntStream.range(0, gapCount)
                .mapToObj(i -> SkillGap.assess(
                        GapFixtures.target(CompetencyId.forCompetency(frameworkId, "SE-" + i),
                                "SE-" + i, 3),
                        GapFixtures.attainment(1, List.of(GapFixtures.evidence(1, 0.5))),
                        POLICY))
                .toList();

        return GapReport.generate(learnerId, frameworkId, gaps,
                Clock.fixed(Instant.parse(generatedAt), ZoneOffset.UTC));
    }

    private void save(GapReport report) {
        repository.save(report);
    }

    private GapReport saveAndReload(GapReport report) {
        repository.save(report);
        entityManager.flush();
        entityManager.clear();
        return repository.findById(report.id()).orElseThrow();
    }

    /** Writes a finding row directly, which is the only way to reach constraints the aggregate
     * already prevents from being violated through it. */
    private void insertRawGap(GapReportId reportId, UUID competencyId, String attainmentValues) {
        jdbc.sql("""
                        insert into gap_analysis.skill_gap
                          (id, report_id, competency_id, competency_code, target_level_code,
                           target_ordinal, attained_ordinal, attained_level_code,
                           attainment_resolved_at, severity)
                        values (?, ?, ?, 'SE-RAW', 'L3', 3, %s, 'MAJOR')
                        """.formatted(attainmentValues))
                .params(UUID.randomUUID(), reportId.value(), competencyId)
                .update();
    }

    private Statistics statistics() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private long countRows(String table) {
        return jdbc.sql("select count(*) from gap_analysis." + table).query(Long.class).single();
    }
}
