package ie.ul.egas.learner;

import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
import ie.ul.egas.learner.domain.model.EvidenceType;
import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;
import ie.ul.egas.learner.domain.policy.UnresolvableEvidenceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The default resolution policy (ADR-018), including the tie-breaks that make it total. Without
 * them an assertion's level would depend on the order its evidence happened to be persisted in,
 * and determinism is part of the port's contract rather than an incidental property.
 */
class LevelResolutionPolicyTests {

    private final LevelResolutionPolicy policy = new HighestConfidenceResolutionPolicy();

    @Test
    void theMostConfidentClaimWinsEvenWhenItClaimsLess() {
        // The substantive judgement in ADR-018: a hesitant claim of L3 loses to a certain L1,
        // because under-stating attainment surfaces a gap that may not exist, whereas over-stating
        // it suppresses one that does.
        List<EvidenceRecord> evidence = List.of(
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.2),
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.95));

        assertThat(policy.resolve(evidence)).isEqualTo(LearnerFixtures.FOUNDATION);
    }

    @Test
    void equalConfidenceIsBrokenByRecency() {
        List<EvidenceRecord> evidence = List.of(
                LearnerFixtures.evidence(EvidenceType.ASSESSMENT, LearnerFixtures.FOUNDATION,
                        0.7, LearnerFixtures.NOW.minusSeconds(3600)),
                LearnerFixtures.evidence(EvidenceType.ASSESSMENT, LearnerFixtures.ADVANCED,
                        0.7, LearnerFixtures.NOW));

        assertThat(policy.resolve(evidence)).isEqualTo(LearnerFixtures.ADVANCED);
    }

    @Test
    void equalConfidenceAndInstantIsBrokenByTheHigherOrdinal() {
        List<EvidenceRecord> ascending = List.of(
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.5));
        List<EvidenceRecord> descending = List.of(
                LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.5),
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5));

        // Order-independence is the property under test: both must agree, or a stored level would
        // depend on persistence sequence.
        assertThat(policy.resolve(ascending)).isEqualTo(LearnerFixtures.INTERMEDIATE);
        assertThat(policy.resolve(descending)).isEqualTo(LearnerFixtures.INTERMEDIATE);
    }

    @Test
    void aSingleRecordResolvesToItsOwnClaim() {
        assertThat(policy.resolve(List.of(LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.4))))
                .isEqualTo(LearnerFixtures.INTERMEDIATE);
    }

    @Test
    void anEmptyEvidenceSetIsUnresolvable() {
        assertThatThrownBy(() -> policy.resolve(List.of()))
                .isInstanceOf(UnresolvableEvidenceException.class);
        assertThatThrownBy(() -> policy.resolve(null))
                .isInstanceOf(UnresolvableEvidenceException.class);
    }

    @Test
    void thePortAdmitsAnEntirelyDifferentRule() {
        // The substitutability ADR-018 claims, demonstrated rather than asserted: a lambda policy
        // that always answers ADVANCED is a valid LevelResolutionPolicy, which is what lets the
        // aggregate tests isolate themselves from resolution arithmetic.
        LevelResolutionPolicy alwaysAdvanced = evidence -> LearnerFixtures.ADVANCED;
        AttainedLevel resolved = alwaysAdvanced.resolve(
                List.of(LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 1.0)));

        assertThat(resolved).isEqualTo(LearnerFixtures.ADVANCED);
    }
}
