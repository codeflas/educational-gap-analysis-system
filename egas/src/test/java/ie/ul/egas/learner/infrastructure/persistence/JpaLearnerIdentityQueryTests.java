package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.learner.LearnerFixtures;
import ie.ul.egas.learner.api.LearnerIdentityQuery;
import ie.ul.egas.learner.domain.LearnerProfileRepository;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published identity contract against real PostgreSQL (ADR-017 Amendment 1).
 *
 * <p>Two properties are worth proving rather than assuming. It must resolve <b>only an identifier</b>
 * — a consumer enforcing ownership has no business receiving a learner's display name, still less
 * their assertion graph — and it must do so <b>without hydrating an aggregate</b>, since ownership is
 * checked on every read and a lookup that loaded a profile's whole evidence history would put that
 * cost on the hot path.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaLearnerProfileRepository.class,
        JpaLearnerIdentityQuery.class})
class JpaLearnerIdentityQueryTests {

    @Autowired
    LearnerProfileRepository profiles;

    @Autowired
    LearnerIdentityQuery identities;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void resolvesAPrincipalToTheLearnerItIdentifies() {
        LearnerProfile profile = profiles.save(LearnerProfile.create(
                new AuthSubject("identity-subject"), new DisplayName("Ada Lovelace"),
                LearnerFixtures.FIXED_CLOCK));
        entityManager.flush();
        entityManager.clear();

        assertThat(identities.learnerIdFor("identity-subject")).contains(profile.id());
    }

    @Test
    void answersEmptyForAPrincipalWithNoProfile() {
        // Provisioning is an explicit act, so a valid token may name nobody (ADR-017). A consumer
        // enforcing ownership reads this as "owns nothing", which is the right answer rather than
        // an error.
        assertThat(identities.learnerIdFor("never-provisioned")).isEmpty();
    }

    @Test
    void answersEmptyRatherThanFailingForAnAbsentOrBlankSubject() {
        assertThat(identities.learnerIdFor(null)).isEmpty();
        assertThat(identities.learnerIdFor("")).isEmpty();
        assertThat(identities.learnerIdFor("   ")).isEmpty();
    }

    @Test
    void distinguishesOneLearnerFromAnother() {
        LearnerProfile first = profiles.save(LearnerProfile.create(
                new AuthSubject("identity-first"), new DisplayName("First"),
                LearnerFixtures.FIXED_CLOCK));
        LearnerProfile second = profiles.save(LearnerProfile.create(
                new AuthSubject("identity-second"), new DisplayName("Second"),
                LearnerFixtures.FIXED_CLOCK));
        entityManager.flush();
        entityManager.clear();

        assertThat(identities.learnerIdFor("identity-first")).contains(first.id());
        assertThat(identities.learnerIdFor("identity-second")).contains(second.id());
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void readsOneColumnWithoutHydratingTheProfile() {
        // Ownership is checked on every read, so this must not become a full aggregate load. It
        // deliberately does not reuse findByAuthSubject, whose entity graph fetches assertions and
        // their evidence.
        profiles.save(profileWithEvidence("identity-cheap"));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(identities.learnerIdFor("identity-cheap")).isPresent();

        assertThat(statistics.getEntityLoadCount())
                .as("an identifier lookup loads no entity at all")
                .isZero();
        assertThat(statistics.getPrepareStatementCount())
                .as("one statement, whatever the profile happens to contain")
                .isEqualTo(1);
    }

    private LearnerProfile profileWithEvidence(String subject) {
        LearnerProfile profile = LearnerProfile.create(
                new AuthSubject(subject), new DisplayName("Evidenced Learner"),
                LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(
                LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.6),
                new HighestConfidenceResolutionPolicy(),
                LearnerFixtures.FIXED_CLOCK);
        return profile;
    }
}
