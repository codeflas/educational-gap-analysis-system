package ie.ul.egas.gapanalysis;

import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three snapshot values that keep a stored finding explicable after its sources have moved on
 * (ADR-021): what was asked for, what was held to be true, and what supported it.
 *
 * <p>None carries identity — two snapshots recording the same facts are the same value — and none
 * carries a reference, which is what lets a report computed in March still be defended in June.
 */
class GapValueObjectTests {

    @Test
    void analysisTargetCopiesTheCompetencyRatherThanPointingAtIt() {
        // A finding holding a reference would change meaning when the framework was revised, so the
        // code and name are copied at computation time (ADR-021).
        AnalysisTarget target = new AnalysisTarget(GapFixtures.SOFTWARE_DESIGN, "SE-DSN",
                "Software Design", "L3", 3);

        assertThat(target.competencyCode()).isEqualTo("SE-DSN");
        assertThat(target.competencyName()).isEqualTo("Software Design");
        assertThat(target.targetLevelCode()).isEqualTo("L3");
        assertThat(target.targetOrdinal()).isEqualTo(3);
    }

    @Test
    void analysisTargetRetainsTheDerivedCompetencyIdentity() {
        // ADR-019 Amendment 1: identity is derived from (framework, code), so a target built from
        // the same pair addresses the same competency without a lookup — the link back to a live
        // competency where one still exists.
        assertThat(GapFixtures.target(2).competencyId()).isEqualTo(GapFixtures.SOFTWARE_DESIGN);
        assertThat(GapFixtures.target(2).competencyId()).isNotEqualTo(GapFixtures.SOFTWARE_TESTING);
    }

    @Test
    void analysisTargetTrimsCodesValidatesAndComparesByValue() {
        assertThat(new AnalysisTarget(GapFixtures.SOFTWARE_DESIGN, "  SE-DSN  ", "Software Design",
                "  L3  ", 3))
                .isEqualTo(GapFixtures.target(3))
                .hasSameHashCodeAs(GapFixtures.target(3));
        assertThat(GapFixtures.target(3)).isNotEqualTo(GapFixtures.target(2));

        assertThatThrownBy(() -> new AnalysisTarget(GapFixtures.SOFTWARE_DESIGN, "  ",
                "Software Design", "L3", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Competency code");
        assertThatThrownBy(() -> new AnalysisTarget(GapFixtures.SOFTWARE_DESIGN, "SE-DSN",
                "Software Design", "  ", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target level code");
        assertThatThrownBy(() -> new AnalysisTarget(GapFixtures.SOFTWARE_DESIGN, "SE-DSN",
                "Software Design", "L0", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisTarget(null, "SE-DSN", "Software Design", "L3", 3))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("competencyId");
    }

    @Test
    void attainmentSnapshotCarriesTheEvidenceBehindTheLevelAndNotOnlyTheLevel() {
        // RQ3's explainability claim rests on this data rather than on prose: a snapshot recording
        // only a resolved level could be displayed but not defended.
        AttainmentSnapshot attainment = GapFixtures.attainment(2,
                List.of(GapFixtures.evidence(1, 0.5), GapFixtures.evidence(2, 0.9)));

        assertThat(attainment.attainedOrdinal()).isEqualTo(2);
        assertThat(attainment.attainedLevelCode()).isEqualTo("L2");
        assertThat(attainment.resolvedAt()).isEqualTo(GapFixtures.NOW);
        assertThat(attainment.evidence()).extracting(EvidenceSnapshot::claimedOrdinal)
                .as("evidence keeps the order it was supplied in")
                .containsExactly(1, 2);
    }

    @Test
    void attainmentSnapshotIsClosedAgainstLaterMutationOfTheSuppliedEvidence() {
        List<EvidenceSnapshot> mutable = new ArrayList<>(List.of(GapFixtures.evidence(1, 0.5)));

        AttainmentSnapshot attainment = GapFixtures.attainment(2, mutable);
        mutable.add(GapFixtures.evidence(3, 0.9));

        assertThat(attainment.evidence())
                .as("the snapshot copied the evidence rather than aliasing the caller's list")
                .hasSize(1);
        assertThat(attainment.evidence()).isUnmodifiable();
    }

    @Test
    void attainmentSnapshotTreatsAbsentEvidenceAsNoneRatherThanAsNull() {
        // A resolved level whose provenance did not survive is degraded but not broken; making
        // callers guard a null would spread the special case through the domain.
        assertThat(new AttainmentSnapshot(2, "L2", GapFixtures.NOW, null).evidence()).isEmpty();
    }

    @Test
    void attainmentSnapshotValidatesAndComparesByValueIncludingItsEvidence() {
        AttainmentSnapshot one = GapFixtures.attainment(2, List.of(GapFixtures.evidence(1, 0.5)));
        AttainmentSnapshot same = GapFixtures.attainment(2, List.of(GapFixtures.evidence(1, 0.5)));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(GapFixtures.attainment(2,
                List.of(GapFixtures.evidence(1, 0.9))));

        assertThatThrownBy(() -> new AttainmentSnapshot(-1, "L0", GapFixtures.NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttainmentSnapshot(1, "  ", GapFixtures.NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attained level code");
        assertThatThrownBy(() -> new AttainmentSnapshot(1, "L1", null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resolvedAt");
    }

    @Test
    void evidenceSnapshotRecordsTheClaimItsStrengthAndWhenItWasMade() {
        EvidenceSnapshot evidence = new EvidenceSnapshot("CERTIFICATION", 3, "L3", 0.95,
                "ISTQB Foundation, 2025", Instant.parse("2025-06-01T00:00:00Z"));

        assertThat(evidence.type())
                .as("a plain string: Learner Profiling flattens its EvidenceType at its own "
                        + "boundary, and re-typing it here would assert a shared vocabulary "
                        + "these contexts do not have")
                .isEqualTo("CERTIFICATION");
        assertThat(evidence.claimedOrdinal()).isEqualTo(3);
        assertThat(evidence.claimedLevelCode()).isEqualTo("L3");
        assertThat(evidence.confidence()).isEqualTo(0.95);
        assertThat(evidence.source()).isEqualTo("ISTQB Foundation, 2025");
        assertThat(evidence.recordedAt()).isEqualTo(Instant.parse("2025-06-01T00:00:00Z"));
    }

    @Test
    void evidenceSnapshotBoundsConfidenceAndRejectsNaN() {
        assertThat(evidenceWithConfidence(0.0).confidence()).isZero();
        assertThat(evidenceWithConfidence(1.0).confidence()).isEqualTo(1.0);

        assertThatThrownBy(() -> evidenceWithConfidence(-0.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence");
        assertThatThrownBy(() -> evidenceWithConfidence(1.01))
                .isInstanceOf(IllegalArgumentException.class);
        // NaN passes a naive range check in both directions, so it is rejected explicitly — the
        // same trap Confidence guards against in Learner Profiling.
        assertThatThrownBy(() -> evidenceWithConfidence(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceSnapshotPermitsAnAbsentSourceButNotAnAbsentTimestamp() {
        // Not every observation has a citable source; every one has a moment it was recorded at,
        // without which the provenance could not be placed in time.
        assertThat(evidenceWithConfidence(0.5).source()).isNull();

        assertThatThrownBy(() -> new EvidenceSnapshot("SELF_DECLARED", 1, "L1", 0.5, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recordedAt");
        assertThatThrownBy(() -> new EvidenceSnapshot("SELF_DECLARED", -1, "L1", 0.5, null,
                GapFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceSnapshotComparesByValue() {
        assertThat(GapFixtures.evidence(2, 0.8))
                .isEqualTo(GapFixtures.evidence(2, 0.8))
                .hasSameHashCodeAs(GapFixtures.evidence(2, 0.8));
        assertThat(GapFixtures.evidence(2, 0.8)).isNotEqualTo(GapFixtures.evidence(2, 0.7));
    }

    private EvidenceSnapshot evidenceWithConfidence(double confidence) {
        return new EvidenceSnapshot("SELF_DECLARED", 1, "L1", confidence, null, GapFixtures.NOW);
    }
}
