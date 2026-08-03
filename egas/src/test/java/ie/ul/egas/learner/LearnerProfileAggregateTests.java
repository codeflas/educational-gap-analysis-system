package ie.ul.egas.learner;

import ie.ul.egas.learner.domain.model.AssertionId;
import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.ConflictingFrameworkException;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
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
        assertThat(profile.isOwnedBy(new AuthSubject("  fixture-learner  "))).isTrue();
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
    void equalityIsByIdentityAloneAndIgnoresState() {
        LearnerProfile profile = LearnerFixtures.profile();
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.5),
                alwaysIntermediate, LearnerFixtures.FIXED_CLOCK);

        // Entity semantics: same identifier, deliberately different state — still the same profile.
        // Asserting this with divergent state is the point; comparing two identical copies would
        // pass under value semantics too and prove nothing about which rule is in force.
        LearnerProfile sameIdDifferentState = LearnerProfile.reconstitute(
                profile.id(), LearnerFixtures.otherSubject(), new DisplayName("Renamed Person"),
                LearnerFixtures.NOW.plusSeconds(86_400), List.of());

        assertThat(sameIdDifferentState).isEqualTo(profile);
        assertThat(sameIdDifferentState).hasSameHashCodeAs(profile);
        assertThat(LearnerFixtures.profileFor("someone-else")).isNotEqualTo(profile);
    }

    @Test
    void reconstitutionRestoresStateFaithfullyAndNotMerelyIdentity() {
        LearnerProfile profile = LearnerFixtures.profile();
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.FOUNDATION, 0.4),
                realPolicy, LearnerFixtures.FIXED_CLOCK);
        profile.recordEvidence(LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.evidence(LearnerFixtures.ADVANCED, 0.9),
                realPolicy, LearnerFixtures.FIXED_CLOCK);

        LearnerProfile rehydrated = LearnerProfile.reconstitute(
                profile.id(), profile.authSubject(), profile.displayName(),
                profile.createdAt(), profile.assertions());

        assertThat(rehydrated.authSubject()).isEqualTo(profile.authSubject());
        assertThat(rehydrated.displayName()).isEqualTo(profile.displayName());
        assertThat(rehydrated.createdAt()).isEqualTo(profile.createdAt());

        // Field-by-field rather than isEqualTo: ProficiencyAssertion's equals is by identifier, so
        // a plain list comparison passes even if every other field were lost in the round trip.
        assertThat(rehydrated.assertions())
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(profile.assertions());

        ProficiencyAssertion restored = rehydrated.assertions().get(0);
        assertThat(restored.competencyId()).isEqualTo(LearnerFixtures.SOFTWARE_DESIGN);
        assertThat(restored.frameworkId()).isEqualTo(LearnerFixtures.FRAMEWORK);
        assertThat(restored.attainedLevel()).isEqualTo(LearnerFixtures.ADVANCED);
        assertThat(restored.evidence()).hasSize(2);
        assertThat(restored.resolvedAt()).isEqualTo(LearnerFixtures.NOW);
    }

    @Test
    void reconstituteTrustsTheStoreAndDoesNotRevalidateAggregateInvariants() {
        // Documents an intentional trust boundary rather than an oversight. Two assertions for one
        // competency is a state recordEvidence can never produce, yet reconstitute admits it:
        // re-validating on load would move invariant checking onto the read path and, worse, make a
        // historical data defect unreadable — and an unreadable record cannot be repaired.
        // recordEvidence is the invariant's guardian; from Phase 2 a unique constraint backs it.
        ProficiencyAssertion first = assertionFor(LearnerFixtures.FOUNDATION);
        ProficiencyAssertion duplicate = assertionFor(LearnerFixtures.ADVANCED);

        LearnerProfile smuggled = LearnerProfile.reconstitute(
                LearnerFixtures.profile().id(), LearnerFixtures.subject(),
                new DisplayName("Smuggled State"), LearnerFixtures.NOW,
                List.of(first, duplicate));

        assertThat(smuggled.assertions())
                .as("rehydration accepts what the store hands it, invariants included")
                .hasSize(2);
        // assertionFor returns the first match, so the aggregate stays usable rather than throwing.
        assertThat(smuggled.assertionFor(LearnerFixtures.SOFTWARE_DESIGN)).contains(first);

        // What rehydration still refuses: structurally malformed components.
        assertThatThrownBy(() -> ProficiencyAssertion.reconstitute(
                AssertionId.random(), LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                LearnerFixtures.FOUNDATION, List.of(), LearnerFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one evidence record");
    }

    /** An assertion built through the rehydration path, for the trust-boundary test above. */
    private ProficiencyAssertion assertionFor(AttainedLevel level) {
        EvidenceRecord evidence = LearnerFixtures.evidence(level, 0.5);
        return ProficiencyAssertion.reconstitute(
                AssertionId.random(), LearnerFixtures.SOFTWARE_DESIGN, LearnerFixtures.FRAMEWORK,
                level, List.of(evidence), LearnerFixtures.NOW);
    }
}
