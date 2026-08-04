package ie.ul.egas.gapanalysis;

import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.gapanalysis.domain.policy.OrdinalDistanceSeverityPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregate root: one learner against one framework, stated as of an instant.
 *
 * <p>Framework-free by construction — the report is assembled from plain objects and a
 * {@link Clock}, so the rules below hold independently of how Phase 4 stores or serves them.
 */
class GapReportTests {

    private final GapSeverityPolicy policy = new OrdinalDistanceSeverityPolicy();

    @Test
    void isStatedAsOfTheInjectedClockRatherThanWallClockTime() {
        // ADR-021: a report describes a moment and stops describing the present as evidence
        // changes, so its timestamp is load-bearing and must be assertable, not merely non-null.
        GapReport report = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(metGap()), GapFixtures.FIXED_CLOCK);

        assertThat(report.generatedAt()).isEqualTo(GapFixtures.NOW);
        assertThat(report.learnerId()).isEqualTo(GapFixtures.LEARNER);
        assertThat(report.frameworkId()).isEqualTo(GapFixtures.FRAMEWORK);
    }

    @Test
    void twoReportsGeneratedFromTheSameInputsAreDistinctRecords() {
        // Each generation is its own record. Nothing dedupes or supersedes: recomputation is an
        // explicit act, and a second report does not silently replace the first.
        GapReport first = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(metGap()), GapFixtures.FIXED_CLOCK);
        GapReport second = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(metGap()), GapFixtures.FIXED_CLOCK);

        assertThat(first).isNotEqualTo(second);
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void findsAGapByTheCompetencyItWasMeasuredFor() {
        SkillGap design = SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy);
        SkillGap testing = SkillGap.assess(
                GapFixtures.target(GapFixtures.SOFTWARE_TESTING, "SE-TST", 2),
                GapFixtures.attainment(2), policy);

        GapReport report = report(design, testing);

        assertThat(report.gapFor(GapFixtures.SOFTWARE_DESIGN)).contains(design);
        assertThat(report.gapFor(GapFixtures.SOFTWARE_TESTING)).contains(testing);
    }

    @Test
    void reportsNoGapForACompetencyItDidNotAnalyse() {
        GapReport report = report(
                SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy));

        assertThat(report.gapFor(GapFixtures.SOFTWARE_TESTING))
                .as("a competency outside the analysis is absent, not a zero gap")
                .isEmpty();
    }

    @Test
    void groupsFindingsBySeverityForAConsumerToActOn() {
        SkillGap met = SkillGap.assess(GapFixtures.target(1), GapFixtures.attainment(1), policy);
        SkillGap major = SkillGap.assess(
                GapFixtures.target(GapFixtures.SOFTWARE_TESTING, "SE-TST", 5),
                GapFixtures.attainment(1), policy);

        GapReport report = report(met, major);

        assertThat(report.gapsOfSeverity(GapSeverity.MET)).containsExactly(met);
        assertThat(report.gapsOfSeverity(GapSeverity.MAJOR)).containsExactly(major);
        assertThat(report.gapsOfSeverity(GapSeverity.MINOR)).isEmpty();
    }

    @Test
    void separatesUnmeasuredCompetenciesFromUnmetOnes() {
        // The two call for different responses — an assessment versus a learning intervention — so
        // a recommender must be able to ask for them separately (ADR-021).
        SkillGap major = SkillGap.assess(GapFixtures.target(5), GapFixtures.attainment(1), policy);
        SkillGap unassessed = SkillGap.unassessed(
                GapFixtures.target(GapFixtures.SOFTWARE_TESTING, "SE-TST", 3), policy);

        GapReport report = report(major, unassessed);

        assertThat(report.unassessedGaps()).containsExactly(unassessed);
        assertThat(report.unassessedGaps()).isUnmodifiable();
        assertThat(report.gapsOfSeverity(GapSeverity.MAJOR))
                .as("an unmeasured competency is not a severe shortfall")
                .containsExactly(major);
        assertThat(report.gapsOfSeverity(GapSeverity.UNASSESSED)).containsExactly(unassessed);
    }

    @Test
    void aFullyMetFrameworkStillProducesAReportWithFindings() {
        GapReport report = report(metGap());

        assertThat(report.gaps()).hasSize(1);
        assertThat(report.unassessedGaps()).isEmpty();
        assertThat(report.gapsOfSeverity(GapSeverity.MET)).hasSize(1);
    }

    @Test
    void aReportWithNoFindingsIsPermittedRatherThanRejected() {
        // A framework with no competencies, or an analysis scoped to none, is a legitimate outcome:
        // rejecting it would push the empty case into the caller as an exception path.
        GapReport report = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(), GapFixtures.FIXED_CLOCK);

        assertThat(report.gaps()).isEmpty();
        assertThat(report.unassessedGaps()).isEmpty();
    }

    @Test
    void isClosedAgainstLaterMutationOfItsFindings() {
        List<SkillGap> mutable = new ArrayList<>(List.of(metGap()));
        GapReport report = GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                mutable, GapFixtures.FIXED_CLOCK);

        mutable.add(SkillGap.unassessed(GapFixtures.target(2), policy));

        assertThat(report.gaps())
                .as("the report copied its findings rather than aliasing the caller's list")
                .hasSize(1);
        assertThat(report.gaps()).isUnmodifiable();
        assertThat(report.gapsOfSeverity(GapSeverity.MET)).isUnmodifiable();
    }

    @Test
    void reconstitutionRestoresIdentityAndTimestampRatherThanMintingNewOnes() {
        // Loading a stored report must not restate when it was made, or every read would quietly
        // present a historical judgement as current.
        GapReportId id = GapReportId.of("33333333-3333-4333-8333-333333333333");
        Instant generatedAt = Instant.parse("2026-03-01T09:00:00Z");
        SkillGap gap = metGap();

        GapReport restored = GapReport.reconstitute(id, GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                generatedAt, List.of(gap));

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.generatedAt()).isEqualTo(generatedAt);
        assertThat(restored.gaps()).containsExactly(gap);
    }

    @Test
    void identityIsByIdentifierNotByContent() {
        GapReportId id = GapReportId.random();

        GapReport one = GapReport.reconstitute(id, GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                GapFixtures.NOW, List.of(metGap()));
        GapReport other = GapReport.reconstitute(id, GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                GapFixtures.NOW, List.of());

        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    void refusesTwoFindingsForTheSameCompetency() {
        // The two could disagree — different targets, different severities — and gapFor() would
        // then return whichever happened to be first, surfacing the ambiguity as an arbitrary
        // answer rather than as an error.
        SkillGap atThree = SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy);
        SkillGap atOne = SkillGap.assess(GapFixtures.target(1), GapFixtures.attainment(1), policy);

        assertThatThrownBy(() -> GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(atThree, atOne), GapFixtures.FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SE-DSN");
    }

    @Test
    void refusesToReconstituteAReportThatViolatesTheOneFindingPerCompetencyRule() {
        // A stored report in this state is corrupt; loading it quietly would carry the corruption
        // into every consumer instead of failing where it can still be diagnosed.
        SkillGap first = SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy);
        SkillGap second = SkillGap.unassessed(GapFixtures.target(3), policy);

        assertThatThrownBy(() -> GapReport.reconstitute(GapReportId.random(), GapFixtures.LEARNER,
                GapFixtures.FRAMEWORK, GapFixtures.NOW, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToBeGeneratedWithoutAClock() {
        assertThatThrownBy(() -> GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK,
                List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock");
    }

    @Test
    void refusesToBeGeneratedWithoutASubjectOrAFramework() {
        Clock clock = Clock.fixed(GapFixtures.NOW, ZoneOffset.UTC);

        assertThatThrownBy(() -> GapReport.generate(null, GapFixtures.FRAMEWORK, List.of(), clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("learnerId");
        assertThatThrownBy(() -> GapReport.generate(GapFixtures.LEARNER, null, List.of(), clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("frameworkId");
    }

    private GapReport report(SkillGap... gaps) {
        return GapReport.generate(GapFixtures.LEARNER, GapFixtures.FRAMEWORK, List.of(gaps),
                GapFixtures.FIXED_CLOCK);
    }

    private SkillGap metGap() {
        return SkillGap.assess(GapFixtures.target(2), GapFixtures.attainment(2), policy);
    }
}
