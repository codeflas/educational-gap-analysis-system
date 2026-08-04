package ie.ul.egas.gapanalysis.infrastructure.persistence;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.api.SkillGapId;
import ie.ul.egas.gapanalysis.domain.GapReportRepository;
import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.domain.model.GapReportSummary;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.learner.api.LearnerId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for stored gap reports (ADR-021). Maps aggregate ↔ mapping entities and
 * delegates SQL to Spring Data.
 *
 * <p><b>Rehydration goes exclusively through {@code reconstitute}</b>, never through {@code assess},
 * {@code unassessed} or {@code generate}. Rebuilding a finding through its write path would re-run
 * the configured {@code GapSeverityPolicy} on every load, so a report computed under one institution's
 * rule would silently restate itself under another's — the reproducibility ADR-021 exists to provide,
 * lost at the last step. It would also mint fresh identifiers for findings that already have
 * published ones.
 *
 * <p><b>This adapter is the trust boundary</b> that {@code GapReport.reconstitute} relies on.
 * Mapping is therefore total and mechanical: no filtering, no deduplication, no defaulting. The one
 * invariant the aggregate does re-check on reconstitution — at most one finding per competency — is
 * backed here by {@code uq_skill_gap_report_competency}, so the check guards against a state the
 * database will not permit rather than one this adapter might produce.
 *
 * <p><b>No exception translation.</b> The learner adapter converts a unique-constraint violation
 * into a domain exception because a caller can provoke one by racing to provision a second profile
 * for the same principal. Nothing here is reachable that way: report identifiers are minted per
 * generation, and the duplicate-competency constraint is already unviolatable through the aggregate.
 * A violation from this table would mean a defect rather than a conflict, and dressing one up as the
 * other would send the next reader to the wrong place entirely. {@code saveAndFlush} is used
 * nonetheless, so that such a defect surfaces at the call site rather than at an arbitrary later
 * flush point where its cause is no longer on the stack.
 */
@Repository
class JpaGapReportRepository implements GapReportRepository {

    /** Matches numeric(4,3); confidence is bounded to [0,1] so only the scale needs fixing. */
    private static final int CONFIDENCE_SCALE = 3;

    private final GapReportSpringDataRepository springData;

    JpaGapReportRepository(GapReportSpringDataRepository springData) {
        this.springData = springData;
    }

    @Override
    @Transactional
    public GapReport save(GapReport report) {
        springData.saveAndFlush(toEntity(report));
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GapReport> findById(GapReportId id) {
        return springData.findWithGapsById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GapReportSummary> findSummariesForLearner(LearnerId learnerId) {
        return springData.findSummariesByLearnerId(learnerId.value()).stream()
                .map(view -> new GapReportSummary(
                        new GapReportId(view.getId()),
                        new CompetencyFrameworkId(view.getFrameworkId()),
                        view.getGeneratedAt(),
                        view.getGapCount()))
                .toList();
    }

    /**
     * Truncates to the precision the column actually holds.
     *
     * <p>Java instants carry nanoseconds and {@code timestamptz} stores microseconds, and PostgreSQL
     * <em>rounds</em> rather than truncates — so an untruncated value reads back different from the
     * one written, and a report would not equal itself across a round trip. Truncating here makes
     * the loss a decision recorded in one place rather than an artefact of the driver. Phase 2 met
     * the same hazard in the projection listener; the difference is that this adapter does not own
     * the instants it is given, so the truncation belongs at the boundary that writes them.
     */
    private static Instant toColumnPrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }

    private GapReportJpaEntity toEntity(GapReport report) {
        GapReportJpaEntity entity = new GapReportJpaEntity(
                report.id().value(),
                report.learnerId().value(),
                report.frameworkId().value(),
                toColumnPrecision(report.generatedAt()));

        for (SkillGap gap : report.gaps()) {
            entity.addGap(toEntity(gap));
        }
        return entity;
    }

    private SkillGapJpaEntity toEntity(SkillGap gap) {
        AnalysisTarget target = gap.target();
        SkillGapJpaEntity entity = new SkillGapJpaEntity(
                gap.id().value(),
                target.competencyId().value(),
                target.competencyCode(),
                target.competencyName(),
                target.targetLevelCode(),
                target.targetOrdinal(),
                gap.severity());

        // Absence is written as absence: an unassessed finding leaves all three attainment columns
        // null rather than storing a zero, which is what lets the distinction survive the round trip.
        gap.attainment().ifPresent(attainment -> entity.recordAttainment(
                attainment.attainedOrdinal(),
                attainment.attainedLevelCode(),
                toColumnPrecision(attainment.resolvedAt())));

        int sequence = 0;
        for (EvidenceSnapshot evidence : gap.evidence()) {
            entity.addEvidence(toEntity(evidence, gap.id(), sequence++));
        }
        return entity;
    }

    /**
     * Evidence rows have no domain identity — {@link EvidenceSnapshot} is a value object — so a row
     * id must be manufactured. It is <em>derived</em> from the owning finding and the record's
     * position rather than generated randomly, so that re-saving a report updates these rows in
     * place instead of inserting a replacement set that would collide with the rows it was about to
     * orphan on {@code uq_gap_evidence_skill_gap_seq}.
     *
     * <p>Position is a safe basis here for a stronger reason than on the learner side, where it
     * rests on evidence being append-only: a gap report has no mutators at all, so a finding's
     * evidence cannot be reordered after the report is made.
     */
    private GapEvidenceJpaEntity toEntity(EvidenceSnapshot evidence, SkillGapId owner, int sequence) {
        return new GapEvidenceJpaEntity(
                evidenceRowId(owner, sequence),
                evidence.type(),
                evidence.claimedOrdinal(),
                evidence.claimedLevelCode(),
                BigDecimal.valueOf(evidence.confidence())
                        .setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP),
                evidence.source(),
                toColumnPrecision(evidence.recordedAt()));
    }

    private static UUID evidenceRowId(SkillGapId owner, int sequence) {
        return UUID.nameUUIDFromBytes(
                (owner.value() + ":" + sequence).getBytes(StandardCharsets.UTF_8));
    }

    private GapReport toDomain(GapReportJpaEntity entity) {
        List<SkillGap> gaps = entity.getGaps().stream()
                .map(this::toDomain)
                .toList();

        return GapReport.reconstitute(
                new GapReportId(entity.getId()),
                new LearnerId(entity.getLearnerId()),
                new CompetencyFrameworkId(entity.getFrameworkId()),
                entity.getGeneratedAt(),
                gaps);
    }

    private SkillGap toDomain(SkillGapJpaEntity entity) {
        AnalysisTarget target = new AnalysisTarget(
                new CompetencyId(entity.getCompetencyId()),
                entity.getCompetencyCode(),
                entity.getCompetencyName(),
                entity.getTargetLevelCode(),
                entity.getTargetOrdinal());

        return SkillGap.reconstitute(
                new SkillGapId(entity.getId()),
                target,
                toAttainment(entity),
                entity.getSeverity());
    }

    /**
     * The attainment snapshot, or {@code null} when nothing was measured.
     *
     * <p>The three columns move together by {@code ck_skill_gap_attainment_complete}, so testing one
     * decides all three. Returning null rather than an empty snapshot is what keeps "never assessed"
     * distinct from "assessed at the lowest level" after a reload.
     */
    private AttainmentSnapshot toAttainment(SkillGapJpaEntity entity) {
        if (entity.getAttainedOrdinal() == null) {
            return null;
        }
        return new AttainmentSnapshot(
                entity.getAttainedOrdinal(),
                entity.getAttainedLevelCode(),
                entity.getAttainmentResolvedAt(),
                entity.getEvidence().stream().map(this::toDomain).toList());
    }

    private EvidenceSnapshot toDomain(GapEvidenceJpaEntity entity) {
        return new EvidenceSnapshot(
                entity.getType(),
                entity.getClaimedOrdinal(),
                entity.getClaimedLevelCode(),
                entity.getConfidence().doubleValue(),
                entity.getSource(),
                entity.getRecordedAt());
    }
}
