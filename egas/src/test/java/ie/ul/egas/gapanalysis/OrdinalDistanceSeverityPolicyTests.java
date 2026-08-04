package ie.ul.egas.gapanalysis;

import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.gapanalysis.domain.policy.OrdinalDistanceSeverityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default severity rule (ADR-021), pinned at its boundaries and at the two simplifications it
 * deliberately makes. ADR-021 names this the one substitutable judgement in the context, so what an
 * institution would be replacing is worth stating precisely rather than leaving to the thresholds.
 */
class OrdinalDistanceSeverityPolicyTests {

    private final GapSeverityPolicy policy = new OrdinalDistanceSeverityPolicy();

    @Test
    void gradesSeverityByTheNumberOfLevelsSeparatingAttainmentFromTheTarget() {
        assertThat(severity(3, 2)).isEqualTo(GapSeverity.MINOR);
        assertThat(severity(3, 1)).isEqualTo(GapSeverity.MODERATE);
        assertThat(severity(3, 0)).isEqualTo(GapSeverity.MAJOR);
        assertThat(severity(9, 0)).isEqualTo(GapSeverity.MAJOR);
    }

    @Test
    void gradesTheThresholdsAtTheirBoundaries() {
        // One and two levels short are the two boundaries the constants encode; three is the first
        // shortfall that falls through to MAJOR. Stated explicitly so a change to either threshold
        // fails here rather than only in a downstream expectation.
        assertThat(severity(1, 0)).isEqualTo(GapSeverity.MINOR);
        assertThat(severity(2, 0)).isEqualTo(GapSeverity.MODERATE);
        assertThat(severity(3, 0)).isEqualTo(GapSeverity.MAJOR);
    }

    @Test
    void meetingTheTargetIsMetAndExceedingItIsAlsoMetRatherThanANegativeGap() {
        // Surplus is not a finding to act on: reporting it as a gap would give a recommender
        // something to remediate where there is nothing to do.
        assertThat(severity(3, 3)).isEqualTo(GapSeverity.MET);
        assertThat(severity(0, 0)).isEqualTo(GapSeverity.MET);
        assertThat(severity(1, 5)).isEqualTo(GapSeverity.MET);
    }

    @Test
    void unmeasuredIsNeverAShortfallHoweverHighTheTargetSits() {
        // The learner may already be beyond the target and simply have nothing recorded. Grading
        // absence as a distance would send a recommender to propose learning when what is missing
        // is an assessment (ADR-021).
        assertThat(policy.severityForUnassessed(GapFixtures.target(0)))
                .isEqualTo(GapSeverity.UNASSESSED);
        assertThat(policy.severityForUnassessed(GapFixtures.target(3)))
                .isEqualTo(GapSeverity.UNASSESSED);
        assertThat(policy.severityForUnassessed(GapFixtures.target(9)))
                .as("a high target does not make an absent measurement a severe gap")
                .isEqualTo(GapSeverity.UNASSESSED);
    }

    @Test
    void ignoresEvidenceConfidenceByDesign() {
        // A documented limitation, not an oversight: a hesitant self-declaration and a confident
        // assessment at the same level are graded alike. ADR-021 expects an institution that cares
        // about the difference to substitute the policy rather than patch this one.
        AttainmentSnapshot hesitant =
                GapFixtures.attainment(2, List.of(GapFixtures.evidence(2, 0.1)));
        AttainmentSnapshot confident =
                GapFixtures.attainment(2, List.of(GapFixtures.evidence(2, 1.0)));

        assertThat(policy.severityFor(GapFixtures.target(3), hesitant))
                .isEqualTo(policy.severityFor(GapFixtures.target(3), confident))
                .isEqualTo(GapSeverity.MINOR);
    }

    @Test
    void ignoresWhereOnTheScaleTheShortfallSitsByDesign() {
        // Every level step is treated as equally significant, which no real proficiency scale
        // guarantees — novice-to-competent is not competent-to-expert. Recorded here so the
        // simplification is visible in the suite and not only in the javadoc.
        assertThat(severity(1, 0)).isEqualTo(severity(9, 8)).isEqualTo(GapSeverity.MINOR);
    }

    @Test
    void thePortAdmitsAnEntirelyDifferentRule() {
        // The substitutability ADR-021 claims, demonstrated rather than asserted — and in
        // particular that a policy may judge absence more harshly than any shortfall, which is the
        // institutional variation the second method exists for.
        GapSeverityPolicy absenceIsWorst = new GapSeverityPolicy() {
            @Override
            public GapSeverity severityFor(AnalysisTarget target, AttainmentSnapshot attainment) {
                return GapSeverity.MINOR;
            }

            @Override
            public GapSeverity severityForUnassessed(AnalysisTarget target) {
                return GapSeverity.MAJOR;
            }
        };

        assertThat(absenceIsWorst.severityFor(GapFixtures.target(9), GapFixtures.attainment(0)))
                .isEqualTo(GapSeverity.MINOR);
        assertThat(absenceIsWorst.severityForUnassessed(GapFixtures.target(1)))
                .isEqualTo(GapSeverity.MAJOR);
    }

    private GapSeverity severity(int targetOrdinal, int attainedOrdinal) {
        return policy.severityFor(GapFixtures.target(targetOrdinal),
                GapFixtures.attainment(attainedOrdinal));
    }
}
