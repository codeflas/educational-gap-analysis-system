package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.learner.LearnerFixtures;
import ie.ul.egas.learner.domain.LearnerProfileRepository;
import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.DuplicateLearnerProfileException;
import ie.ul.egas.learner.domain.model.EvidenceType;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.ProficiencyAssertion;
import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter contract against real PostgreSQL 16 (Testcontainers): Flyway schema, full aggregate
 * round-trip fidelity, evidence ordering, constraint translation, and column-only summaries.
 *
 * <p>These tests are what discharges the trust boundary {@code LearnerProfile.reconstitute}
 * documents. Because the aggregate rehydrates without re-validating, "what was written is what
 * comes back" is a claim only this suite can substantiate — so assertions compare state
 * field-by-field rather than by identifier, which entity equality would satisfy vacuously.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaLearnerProfileRepository.class})
class JpaLearnerProfileRepositoryTests {

    @Autowired
    LearnerProfileRepository repository;

    @PersistenceContext
    EntityManager entityManager;

    private final LevelResolutionPolicy policy = new HighestConfidenceResolutionPolicy();

    @Test
    void savesAndRehydratesTheWholeAggregateFieldForField() {
        LearnerProfile saved = repository.save(profileWithTwoAssertions("round-trip-subject"));

        LearnerProfile reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.authSubject()).isEqualTo(saved.authSubject());
        assertThat(reloaded.displayName()).isEqualTo(saved.displayName());
        assertThat(reloaded.createdAt()).isEqualTo(saved.createdAt());
        // Field-by-field: ProficiencyAssertion equals by identifier, so a plain list comparison
        // would pass even if every other column had been lost in the round trip.
        assertThat(reloaded.assertions())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(saved.assertions());
    }

    @Test
    void evidenceOrderSurvivesReload() {
        LearnerProfile saved = repository.save(profileWithTwoAssertions("evidence-order-subject"));

        ProficiencyAssertion reloaded = repository.findById(saved.id()).orElseThrow()
                .assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow();

        assertThat(reloaded.evidence())
                .as("evidence is append-only and exposed oldest-first (ADR-018)")
                .extracting(record -> record.claimedLevel().code())
                .containsExactly("L1", "L3");
    }

    @Test
    void assertionIdentitySurvivesReload() {
        LearnerProfile saved = repository.save(profileWithTwoAssertions("assertion-id-subject"));
        ProficiencyAssertion original =
                saved.assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow();

        ProficiencyAssertion reloaded = repository.findById(saved.id()).orElseThrow()
                .assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow();

        assertThat(reloaded.id())
                .as("identifiers are persisted, never regenerated on load")
                .isEqualTo(original.id());
    }

    @Test
    void confidencePrecisionSurvivesTheBigDecimalRoundTrip() {
        LearnerProfile profile = LearnerProfile.create(
                new AuthSubject("confidence-subject"), new DisplayName("Precision"),
                LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.825),
                policy, LearnerFixtures.FIXED_CLOCK);
        LearnerProfile saved = repository.save(profile);

        var reloaded = repository.findById(saved.id()).orElseThrow()
                .assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow()
                .evidence().get(0);

        assertThat(reloaded.confidence().value()).isEqualTo(0.825);
        assertThat(reloaded.source()).isEqualTo("fixture evidence");
        assertThat(reloaded.type()).isEqualTo(EvidenceType.SELF_DECLARED);
        assertThat(reloaded.recordedAt()).isEqualTo(LearnerFixtures.NOW);
    }

    @Test
    void attainedLevelOrdinalAndCodeBothSurvive() {
        LearnerProfile saved = repository.save(profileWithTwoAssertions("level-subject"));

        ProficiencyAssertion reloaded = repository.findById(saved.id()).orElseThrow()
                .assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow();

        AttainedLevel level = reloaded.attainedLevel();
        assertThat(level.ordinal()).isEqualTo(3);
        assertThat(level.code()).isEqualTo("L3");
        assertThat(level).isEqualTo(LearnerFixtures.ADVANCED);
        assertThat(reloaded.frameworkId()).isEqualTo(LearnerFixtures.FRAMEWORK);
        assertThat(reloaded.competencyId()).isEqualTo(LearnerFixtures.SOFTWARE_DESIGN);
    }

    @Test
    void savingAnExistingProfileAccumulatesEvidenceRatherThanColliding() {
        // The gap that let a defect through review: every other test saves a profile exactly once,
        // yet recording evidence against an existing profile is this module's primary use case.
        // While evidence row ids were random, the second save inserted a replacement set that
        // collided with its own orphans on uq_evidence_assertion_seq.
        LearnerProfile saved = repository.save(profileWithTwoAssertions("update-subject"));
        entityManager.flush();
        entityManager.clear();

        LearnerProfile reloaded = repository.findById(saved.id()).orElseThrow();
        var originalAssertionId =
                reloaded.assertionFor(LearnerFixtures.SOFTWARE_TESTING).orElseThrow().id();
        reloaded.recordEvidence(LearnerFixtures.SOFTWARE_TESTING, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.95),
                policy, LearnerFixtures.FIXED_CLOCK);

        repository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        LearnerProfile after = repository.findById(saved.id()).orElseThrow();
        ProficiencyAssertion testing =
                after.assertionFor(LearnerFixtures.SOFTWARE_TESTING).orElseThrow();

        assertThat(after.assertions())
                .as("an update must not multiply assertions")
                .hasSize(2);
        assertThat(testing.evidence())
                .as("evidence accumulates; it is appended beside, never replaced")
                .hasSize(2);
        assertThat(testing.id())
                .as("assertion identity survives an update")
                .isEqualTo(originalAssertionId);
        assertThat(testing.evidence())
                .extracting(record -> record.claimedLevel().code())
                .containsExactly("L2", "L3");
        assertThat(testing.attainedLevel())
                .as("the more confident later claim wins on re-resolution")
                .isEqualTo(LearnerFixtures.ADVANCED);
        assertThat(after.assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow().evidence())
                .as("the untouched assertion is left exactly as it was")
                .hasSize(2);
    }

    @Test
    void translatesTheDuplicateAuthSubjectConstraintIntoTheDomainException() {
        repository.save(profileWithTwoAssertions("duplicate-subject"));

        assertThatThrownBy(() -> repository.save(profileWithTwoAssertions("duplicate-subject")))
                .isInstanceOf(DuplicateLearnerProfileException.class);
    }

    @Test
    void theDatabaseRejectsASecondAssertionForTheSameCompetency() {
        // Bypasses the aggregate entirely: this is the second line of defence that discharges the
        // reconstitute() trust assumption (ADR-020). The invariant must hold even against a
        // defective adapter or a hand-edited row, not only against recordEvidence.
        LearnerProfile saved = repository.save(profileWithTwoAssertions("constraint-subject"));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    insert into learner.proficiency_assertion
                        (id, profile_id, competency_id, framework_id, level_ordinal, level_code, resolved_at)
                    values (?, ?, ?, ?, ?, ?, now())
                    """)
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, saved.id().value())
                    .setParameter(3, LearnerFixtures.SOFTWARE_DESIGN.value())
                    .setParameter(4, LearnerFixtures.FRAMEWORK.value())
                    .setParameter(5, 1)
                    .setParameter(6, "L1")
                    .executeUpdate();
            entityManager.flush();
        })
                // Asserted on the constraint name rather than an exception type: this insert goes
                // through the EntityManager, not a Spring Data repository, so Spring's exception
                // translation does not apply and the wrapper type is an implementation detail.
                // That the named constraint fired is the property under test.
                .hasStackTraceContaining("uq_assertion_profile_competency");
    }

    @Test
    void findsAProfileByItsAuthenticatedSubject() {
        LearnerProfile saved = repository.save(profileWithTwoAssertions("lookup-subject"));

        assertThat(repository.findByAuthSubject(new AuthSubject("lookup-subject")))
                .isPresent()
                .get()
                .extracting(LearnerProfile::id)
                .isEqualTo(saved.id());
        // AuthSubject trims on construction, so incidental padding must still resolve.
        assertThat(repository.findByAuthSubject(new AuthSubject("  lookup-subject  "))).isPresent();
        assertThat(repository.findByAuthSubject(new AuthSubject("no-such-subject"))).isEmpty();
    }

    @Test
    void reportsExistenceByAuthSubject() {
        repository.save(profileWithTwoAssertions("exists-subject"));

        assertThat(repository.existsByAuthSubject(new AuthSubject("exists-subject"))).isTrue();
        assertThat(repository.existsByAuthSubject(new AuthSubject("absent-subject"))).isFalse();
    }

    @Test
    void listsSummariesWithCountsWithoutLoadingEvidence() {
        repository.save(profileWithTwoAssertions("summary-subject-a"));
        repository.save(profileWithTwoAssertions("summary-subject-b"));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var summaries = repository.findAllSummaries();

        assertThat(summaries).extracting(s -> s.displayName().value()).contains("Test Learner");
        assertThat(summaries).allSatisfy(summary ->
                assertThat(summary.assertionCount()).isEqualTo(2));
        // The performance gate, measured rather than assumed: an interface projection loads no
        // entities at all, so neither assertion nor evidence rows are hydrated on the list path.
        assertThat(statistics.getEntityLoadCount())
                .as("listing must be a projection — no entity graph may be materialised")
                .isZero();
    }

    /** Two assertions; the first carries two evidence records so ordering is observable. */
    private LearnerProfile profileWithTwoAssertions(String subject) {
        LearnerProfile profile = LearnerProfile.create(
                new AuthSubject(subject), new DisplayName("Test Learner"),
                LearnerFixtures.FIXED_CLOCK);

        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.4),
                policy, LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.9),
                policy, LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_TESTING, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.INTERMEDIATE, 0.7),
                policy, LearnerFixtures.FIXED_CLOCK);
        return profile;
    }
}
