package ie.ul.egas.learner;

import ie.ul.egas.learner.domain.model.ConflictingFrameworkException;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.ProficiencyAssertion;
import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregate's invariants, in isolation.
 *
 * <p>Two properties of this suite are the point of it. It uses <b>no security infrastructure</b> —
 * no security context, no token, no Spring — yet exercises the ownership rule directly, which is
 * the payoff ADR-016 argued for when it made the caller's identity command data rather than
 * ambient state. And it stubs {@link LevelResolutionPolicy} with a lambda wherever the resolved
 * value is incidental, so an aggregate invariant never fails because resolution arithmetic
 * changed — the seam ADR-018 created for exactly this.
 */
class LearnerProfileAggregateTests {

    private final LevelResolutionPolicy realPolicy = new HighestConfidenceResolutionPolicy();
    private final LevelResolutionPolicy alwaysIntermediate = evidence -> LearnerFixtures.INTERMEDIATE;

    @Test
    void createsAProfileWithIndependentIdentityAndAFixedTimestamp() {
        LearnerProfile profile = LearnerProfile.create(
                LearnerFixtures.subject(), new DisplayName("Ada Lovelace"), LearnerFixtures.FIXED_CLOCK);

        assertThat(profile.id()).isNotNull();
        assertThat(profile.id().value()).isNotNull();
        assertThat(profile.authSubject()).isEqualTo(LearnerFixtures.subject());
        assertThat(profile.displayName().value()).isEqualTo("Ada Lovelace");
        assertThat(profile.createdAt()).isEqualTo(LearnerFixtures.NOW);
        assertThat(profile.assertions()).isEmpty();
    }

    @Test
    void theFirstEvidenceOpensAnAssertionResolvedByThePolicy() {
        LearnerProfile profile = LearnerFixtures.profile();

        ProficiencyAssertion opened = profile.recordEvidence(
                LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.6),
                realPolicy, LearnerFixtures.FIXED_CLOCK);

        assertThat(profile.assertions()).containsExactly(opened);
        assertThat(opened.competencyId()).isEqualTo(LearnerFixtures.SOFTWARE_DESIGN);
        assertThat(opened.frameworkId()).isEqualTo(LearnerFixtures.FRAMEWORK);
        assertThat(opened.attainedLevel()).isEqualTo(LearnerFixtures.FOUNDATION);
        assertThat(opened.evidence()).hasSize(1);
        assertThat(opened.resolvedAt()).isEqualTo(LearnerFixtures.NOW);
    }

    @Test
    void furtherEvidenceExtendsTheSameAssertionAndReResolvesTheLevel() {
        LearnerProfile profile = LearnerFixtures.profile();
        ProficiencyAssertion first = profile.recordEvidence(
                LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.4),
                realPolicy, LearnerFixtures.FIXED_CLOCK);

        ProficiencyAssertion updated = profile.recordEvidence(
                LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.9),
                realPolicy, LearnerFixtures.FIXED_CLOCK);

        assertThat(profile.assertions()).hasSize(1);
        assertThat(updated.id())
                .as("identity survives an update; only state is replaced")
                .isEqualTo(first.id());
        assertThat(updated.evidence()).hasSize(2);
        assertThat(updated.attainedLevel()).isEqualTo(LearnerFixtures.ADVANCED);
        assertThat(first.evidence())
                .as("the superseded instance is unchanged — no caller observes state mutate underneath it")
                .hasSize(1);
    }

    @Test
    void holdsAtMostOneAssertionPerCompetencyButOnePerCompetency() {
        LearnerProfile profile = LearnerFixtures.profile();

        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_TESTING, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);

        assertThat(profile.assertions()).hasSize(2);
        assertThat(profile.assertionFor(LearnerFixtures.SOFTWARE_DESIGN))
                .isPresent()
                .get()
                .extracting(assertion -> assertion.evidence().size())
                .isEqualTo(2);
        assertThat(profile.assertionFor(LearnerFixtures.randomCompetency())).isEmpty();
    }

    @Test
    void rejectsEvidenceSuppliedUnderAConflictingFramework() {
        LearnerProfile profile = LearnerFixtures.profile();
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);

        assertThatThrownBy(() -> profile.recordEvidence(
                LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.OTHER_FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.9),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK))
                .isInstanceOf(ConflictingFrameworkException.class)
                .hasMessageContaining("belongs to exactly one framework");

        assertThat(profile.assertions().get(0).evidence())
                .as("a rejected record leaves the assertion untouched")
                .hasSize(1);
    }

    @Test
    void ownershipIsDecidedByTheAuthenticatedSubjectWithNoSecurityInfrastructure() {
        LearnerProfile profile = LearnerFixtures.profile();

        assertThat(profile.isOwnedBy(LearnerFixtures.subject())).isTrue();
        assertThat(profile.isOwnedBy(LearnerFixtures.otherSubject())).isFalse();
        // Whitespace insensitivity matters: the subject arrives from a token claim, and a profile
        // must not become unreachable because of incidental padding.
        assertThat(profile.isOwnedBy(new ie.ul.egas.learner.domain.model.AuthSubject("  test-learner  ")))
                .isTrue();
    }

    @Test
    void exposesAssertionsAndEvidenceAsUnmodifiableViews() {
        LearnerProfile profile = LearnerFixtures.profile();
        ProficiencyAssertion assertion = profile.recordEvidence(
                LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);

        List<ProficiencyAssertion> assertions = profile.assertions();
        assertThatThrownBy(assertions::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> assertion.evidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalityIsByIdentityAndSurvivesReconstitution() {
        LearnerProfile profile = LearnerFixtures.profile();
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);

        LearnerProfile rehydrated = LearnerProfile.reconstitute(
                profile.id(), profile.authSubject(), profile.displayName(),
                profile.createdAt(), profile.assertions());

        assertThat(rehydrated).isEqualTo(profile);
        assertThat(rehydrated.assertions()).isEqualTo(profile.assertions());
        assertThat(LearnerFixtures.profileFor("someone-else")).isNotEqualTo(profile);
    }
}
