package ie.ul.egas.learner;

import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.Confidence;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
import ie.ul.egas.learner.domain.model.EvidenceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value objects: validating, normalising, ordered where ordering is meaningful. */
class LearnerValueObjectTests {

    @Test
    void authSubjectTrimsAndBoundsButNeverInterprets() {
        // Opaque by design (ADR-017): an email, a UUID and a username are all equally acceptable,
        // so a future identity source can change the content without changing this type.
        assertThat(new AuthSubject("  test-learner  ").value()).isEqualTo("test-learner");
        assertThat(new AuthSubject("a.user@ul.ie").value()).isEqualTo("a.user@ul.ie");
        assertThatThrownBy(() -> new AuthSubject("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthSubject("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayNameTrimsAndBounds() {
        assertThat(new DisplayName("  Ada Lovelace ").value()).isEqualTo("Ada Lovelace");
        assertThatThrownBy(() -> new DisplayName("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DisplayName("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attainedLevelValidatesAndOrdersByOrdinal() {
        assertThat(new AttainedLevel(2, "  L2 ").code()).isEqualTo("L2");
        assertThat(new AttainedLevel(3, "L3")).isGreaterThan(new AttainedLevel(2, "L2"));
        assertThat(new AttainedLevel(2, "L2")).isEqualByComparingTo(new AttainedLevel(2, "SFIA-2"));
        assertThatThrownBy(() -> new AttainedLevel(-1, "L1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttainedLevel(1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidenceRejectsOutOfRangeNaNAndInfinity() {
        assertThat(new Confidence(0.0).value()).isZero();
        assertThat(Confidence.CERTAIN.value()).isEqualTo(1.0);
        assertThatThrownBy(() -> new Confidence(1.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Confidence(-0.01)).isInstanceOf(IllegalArgumentException.class);
        // NaN passes a naive range check in both directions, so it is rejected explicitly.
        assertThatThrownBy(() -> new Confidence(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
        assertThatThrownBy(() -> new Confidence(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceRecordNormalisesSourceAndRequiresEveryComponent() {
        EvidenceRecord record = LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.8);
        assertThat(record.type()).isEqualTo(EvidenceType.SELF_DECLARED);
        assertThat(record.confidence().value()).isEqualTo(0.8);

        assertThatThrownBy(() -> new EvidenceRecord(EvidenceType.ASSESSMENT,
                LearnerFixtures.INTERMEDIATE, Confidence.CERTAIN, "   ", LearnerFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceRecord(null,
                LearnerFixtures.INTERMEDIATE, Confidence.CERTAIN, "src", LearnerFixtures.NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
