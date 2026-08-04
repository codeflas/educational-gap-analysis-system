package ie.ul.egas.gapanalysis;

import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.gapanalysis.domain.policy.OrdinalDistanceSeverityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A single finding: what it records, what it computes, and what it refuses to conflate.
 *
 * <p>No Spring, no database, no security context — the whole gap model is exercisable as plain
 * objects, which is the property that will let Phase 4's application service be tested the same
 * way.
 */
class SkillGapTests {

    private final GapSeverityPolicy policy = new OrdinalDistanceSeverityPolicy();

    /** A policy that answers differently for the two cases, to prove the aggregate delegates both. */
    private final GapSeverityPolicy stubPolicy = new GapSeverityPolicy() {
        @Override
        public GapSeverity severityFor(AnalysisTarget target, AttainmentSnapshot attainment) {
            return GapSeverity.MODERATE;
        }

        @Override
        public GapSeverity severityForUnassessed(AnalysisTarget target) {
            return GapSeverity.MAJOR;
        }
    };

    @Test
    void recordsTheTargetItWasMeasuredAgainst() {
        // The same attainment yields a different gap under a different target, so a finding that
        // omitted its target could not be defended later (ADR-021).
        SkillGap gap = SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy);

        assertThat(gap.target().targetOrdinal()).isEqualTo(3);
        assertThat(gap.target().targetLevelCode()).isEqualTo("L3");
        assertThat(gap.target().competencyId()).isEqualTo(GapFixtures.SOFTWARE_DESIGN);
        assertThat(gap.target().competencyCode()).isEqualTo("SE-DSN");
    }

    @Test
    void computesShortfallAsTheDistanceToTheTarget() {
        assertThat(SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy)
                .shortfall()).hasValue(2);
        assertThat(SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(3), policy)
                .shortfall()).hasValue(0);
    }

    @Test
    void exceedingTheTargetIsNoShortfallRatherThanANegativeOne() {
        SkillGap gap = SkillGap.assess(GapFixtures.target(2), GapFixtures.attainment(4), policy);

        assertThat(gap.shortfall())
                .as("surplus is not a finding to act on")
                .hasValue(0);
        assertThat(gap.severity()).isEqualTo(GapSeverity.MET);
    }

    @Test
    void unassessedIsDistinguishableFromAttainedAtTheLowestLevel() {
        // The distinction ADR-021 protects: nothing measured is a different problem from measured
        // and far behind, and the remedies differ — assessment versus learning.
        SkillGap unassessed = SkillGap.unassessed(GapFixtures.target(3), policy);
        SkillGap lowest = SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(0), policy);

        assertThat(unassessed.isUnassessed()).isTrue();
        assertThat(unassessed.attainment()).isEmpty();
        assertThat(unassessed.shortfall())
                .as("no distance has been measured, so there is no shortfall to report")
                .isEmpty();
        assertThat(unassessed.severity()).isEqualTo(GapSeverity.UNASSESSED);

        assertThat(lowest.isUnassessed()).isFalse();
        assertThat(lowest.attainment()).isPresent();
        assertThat(lowest.shortfall()).hasValue(3);
        assertThat(lowest.severity()).isNotEqualTo(GapSeverity.UNASSESSED);
    }

    @Test
    void preservesTheExplainabilityChainFromTargetThroughAttainmentToEvidence() {
        List<EvidenceSnapshot> evidence = List.of(
                GapFixtures.evidence(1, 0.4),
                GapFixtures.evidence(2, 0.9));

        SkillGap gap = SkillGap.assess(GapFixtures.target(3),
                GapFixtures.attainment(2, evidence), policy);

        // target -> attainment -> evidence, all retained in the finding itself (ADR-021, RQ3).
        assertThat(gap.target().targetLevelCode()).isEqualTo("L3");
        assertThat(gap.attainment()).isPresent()
                .get().extracting(AttainmentSnapshot::attainedLevelCode).isEqualTo("L2");
        assertThat(gap.evidence()).hasSize(2);
        assertThat(gap.evidence()).extracting(EvidenceSnapshot::confidence)
                .containsExactly(0.4, 0.9);
        assertThat(gap.evidence()).extracting(EvidenceSnapshot::source)
                .allMatch("fixture evidence"::equals);
        assertThat(gap.evidence().get(0).recordedAt()).isEqualTo(GapFixtures.NOW);
    }

    @Test
    void anUnassessedFindingCarriesNoEvidence() {
        assertThat(SkillGap.unassessed(GapFixtures.target(2), policy).evidence()).isEmpty();
    }

    @Test
    void severityComesFromThePolicyForBothTheAssessedAndUnassessedCase() {
        // Proves the aggregate delegates rather than deciding: a stub answering differently from
        // the default must be visible in both outcomes.
        assertThat(SkillGap.assess(GapFixtures.target(1), GapFixtures.attainment(1), stubPolicy)
                .severity())
                .as("the aggregate must not shortcut a met target to MET on its own")
                .isEqualTo(GapSeverity.MODERATE);
        assertThat(SkillGap.unassessed(GapFixtures.target(1), stubPolicy).severity())
                .isEqualTo(GapSeverity.MAJOR);
    }

    @Test
    void reconstitutionRestoresTheStoredJudgementRatherThanRecomputingIt() {
        // Re-running a policy on load would silently rewrite history whenever the configured rule
        // changed — the same non-behaviour ADR-018 records for level resolution.
        SkillGap original = SkillGap.assess(GapFixtures.target(3), GapFixtures.attainment(1), policy);

        SkillGap restored = SkillGap.reconstitute(original.id(), original.target(),
                original.attainment().orElseThrow(), GapSeverity.MET);

        assertThat(restored.severity())
                .as("the stored severity wins, even where the default policy would disagree")
                .isEqualTo(GapSeverity.MET);
        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void identityIsByIdentifierAndFindingsAreValueStable() {
        SkillGap gap = SkillGap.assess(GapFixtures.target(2), GapFixtures.attainment(1), policy);

        assertThat(gap).isEqualTo(gap);
        assertThat(gap).isNotEqualTo(
                SkillGap.assess(GapFixtures.target(2), GapFixtures.attainment(1), policy));
        assertThat(gap.evidence()).isUnmodifiable();
        assertThat(gap.attainment().orElseThrow().evidence()).isUnmodifiable();
    }

    @Test
    void refusesAMissingAttainmentInsteadOfSilentlyTreatingItAsUnassessed() {
        assertThatThrownBy(() -> SkillGap.assess(GapFixtures.target(2), null, policy))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("unassessed");
    }
}
