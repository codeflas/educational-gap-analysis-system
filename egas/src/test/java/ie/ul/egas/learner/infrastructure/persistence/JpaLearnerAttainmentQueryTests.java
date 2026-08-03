package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.learner.LearnerFixtures;
import ie.ul.egas.learner.api.AttainedCompetency;
import ie.ul.egas.learner.api.LearnerAttainmentQuery;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.LearnerProfileRepository;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.EvidenceType;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published attainment contract against real PostgreSQL (ADR-022).
 *
 * <p>The assertion that matters most is that evidence travels with the level. ADR-021's
 * explainability chain runs analysis target → attainment → evidence, and Gap Analysis sits on the
 * far side of a module boundary: if the contract drops provenance here, a downstream gap can be
 * reported but never defended, and RQ3's claim becomes prose.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaLearnerProfileRepository.class,
        JpaLearnerAttainmentQuery.class})
class JpaLearnerAttainmentQueryTests {

    @Autowired
    LearnerProfileRepository repository;

    @Autowired
    LearnerAttainmentQuery query;

    private final LevelResolutionPolicy policy = new HighestConfidenceResolutionPolicy();

    @Test
    void returnsResolvedAttainmentWithItsSupportingEvidence() {
        LearnerProfile profile = profileWith("attainment-subject");

        var attainments = query.attainmentsFor(profile.id());

        assertThat(attainments).hasSize(1);
        AttainedCompetency attained = attainments.get(0);
        assertThat(attained.competencyId()).isEqualTo(LearnerFixtures.SOFTWARE_DESIGN);
        assertThat(attained.frameworkId()).isEqualTo(LearnerFixtures.FRAMEWORK);
        assertThat(attained.attainedOrdinal()).isEqualTo(3);
        assertThat(attained.attainedLevelCode()).isEqualTo("L3");
        assertThat(attained.resolvedAt()).isEqualTo(LearnerFixtures.NOW);

        assertThat(attained.evidence())
                .as("provenance must cross the boundary, or a downstream gap cannot be explained")
                .hasSize(2);
        assertThat(attained.evidence()).extracting(AttainedCompetency.EvidenceSummary::claimedLevelCode)
                .containsExactly("L1", "L3");
        assertThat(attained.evidence().get(0).type()).isEqualTo(EvidenceType.SELF_DECLARED.name());
        assertThat(attained.evidence().get(0).confidence()).isEqualTo(0.4);
        assertThat(attained.evidence().get(0).source()).isEqualTo("fixture evidence");
    }

    @Test
    void returnsEmptyForALearnerWithNoProfile() {
        // A valid identity does not imply enrolment (ADR-017). Gap Analysis treats the absence as
        // "has attained nothing" rather than as an error, which is the correct reading.
        assertThat(query.attainmentsFor(LearnerId.random())).isEmpty();
    }

    @Test
    void reportsEveryCompetencyTheLearnerHoldsAnAssertionFor() {
        LearnerProfile profile = profileWith("multi-competency-subject");
        profile.recordEvidence(LearnerFixtures.SOFTWARE_TESTING, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.7),
                policy, LearnerFixtures.FIXED_CLOCK);
        repository.save(profile);

        assertThat(query.attainmentsFor(profile.id()))
                .extracting(AttainedCompetency::competencyId)
                .containsExactlyInAnyOrder(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.SOFTWARE_TESTING);
    }

    private LearnerProfile profileWith(String subject) {
        LearnerProfile profile = LearnerProfile.create(
                new AuthSubject(subject), new DisplayName("Attainment Learner"),
                LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.4),
                policy, LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.9),
                policy, LearnerFixtures.FIXED_CLOCK);
        return repository.save(profile);
    }
}
