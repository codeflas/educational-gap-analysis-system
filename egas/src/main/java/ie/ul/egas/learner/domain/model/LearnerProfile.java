package ie.ul.egas.learner.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root: a learner's profile — who it belongs to, and what they are held to have attained.
 *
 * <p><b>Aggregate boundary.</b> {@link ProficiencyAssertion} is inside this boundary rather than an
 * aggregate of its own, because the invariant <em>at most one assertion per competency</em>
 * requires a transaction to enforce: recording evidence must either extend the competency's
 * existing assertion or open the first one, and two concurrent writers cannot both make that
 * decision correctly. The database's unique constraint backs the rule as a second line of defence,
 * but the decision itself is made here. The cost — recording one observation loads the whole
 * profile — is bounded by realistic framework size and is measured rather than assumed; ADR-018
 * and the Step 4 plan record the escape hatch should profiles ever grow large.
 *
 * <p><b>Identity.</b> {@link LearnerId} is independent of the authenticating principal, per the
 * contract fixed in Step 1 and ratified by ADR-017: a change of identity provider must not re-key
 * every learner. The {@link AuthSubject} is carried as an opaque attribute so the mapping is a
 * single indexed lookup rather than a second persistence structure.
 *
 * <p><b>Ownership lives here, not in a filter chain.</b> {@link #isOwnedBy(AuthSubject)} is domain
 * logic: it is a fact about this profile, expressible only with the profile in hand, which is
 * exactly why ADR-015's URL patterns cannot state it and why its amendment moves the predicate to
 * the application layer. Because the caller's identity arrives as an ordinary value (ADR-016), the
 * whole ownership rule is testable with no security infrastructure whatsoever — the payoff that
 * ADR made the case for.
 *
 * <p>The aggregate is framework-free: no Spring, no persistence, no serialisation. Its only
 * external vocabulary is {@code competency :: api} identifiers, referenced by value, never
 * resolved (ADR-019).
 */
public final class LearnerProfile {

    private final LearnerId id;
    private final AuthSubject authSubject;
    private final DisplayName displayName;
    private final Instant createdAt;
    private final List<ProficiencyAssertion> assertions;

    private LearnerProfile(LearnerId id, AuthSubject authSubject, DisplayName displayName,
                           Instant createdAt, List<ProficiencyAssertion> assertions) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.authSubject = Objects.requireNonNull(authSubject, "authSubject must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.assertions = new ArrayList<>(Objects.requireNonNull(assertions, "assertions must not be null"));
    }

    /**
     * Creates a profile for a newly enrolled principal. Provisioning is an explicit act rather
     * than a side effect of first access (ADR-017), so the moment a person entered the system is
     * a moment the system can point at.
     */
    public static LearnerProfile create(AuthSubject authSubject, DisplayName displayName, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new LearnerProfile(
                LearnerId.random(), authSubject, displayName, clock.instant(), List.of());
    }

    /**
     * Rehydrates a persisted profile, trusting the store.
     *
     * <p><b>Aggregate invariants are deliberately not re-checked here.</b> This method will accept
     * two assertions for the same competency — a state {@link #recordEvidence} can never produce.
     * That is a recorded decision, not an oversight, and it rests on two arguments. Re-validating
     * on every load moves invariant checking onto the read path, where it is paid on every request
     * to protect against a defect that can only be introduced on the write path. More decisively,
     * a profile that fails validation on load becomes <em>unreadable</em>, and an unreadable record
     * cannot be inspected or repaired — a historical data defect would escalate into an outage for
     * that learner rather than a report against them.
     *
     * <p><b>What is still enforced.</b> Null arguments are rejected, and every component validates
     * itself on construction: {@link AuthSubject}, {@link DisplayName} and {@link AttainedLevel}
     * apply their own rules, and {@link ProficiencyAssertion} still refuses an empty evidence set.
     * Rehydration cannot produce a structurally malformed object, only a semantically stale one.
     *
     * <p><b>Where the line is actually held.</b> The write path is the sole author of aggregate
     * state, so {@link #recordEvidence} is the invariant's real guardian; from Phase 2 a unique
     * constraint on (profile, competency) backs it in the database, as the class documentation
     * states. The trust boundary is therefore the persistence adapter: its round-trip tests are
     * what must demonstrate that what was written is what comes back.
     */
    public static LearnerProfile reconstitute(LearnerId id, AuthSubject authSubject,
                                              DisplayName displayName, Instant createdAt,
                                              List<ProficiencyAssertion> assertions) {
        return new LearnerProfile(id, authSubject, displayName, createdAt, assertions);
    }

    /**
     * Records one observation against a competency, resolving the attained level across the whole
     * evidence set for that competency.
     *
     * <p>Extends the competency's existing assertion if there is one, opens the first otherwise —
     * the single place the one-assertion-per-competency invariant is decided.
     *
     * @throws ConflictingFrameworkException if the competency already has an assertion opened
     *                                       under a different framework
     * @return the affected assertion, resolved
     */
    public ProficiencyAssertion recordEvidence(CompetencyId competencyId,
                                               CompetencyFrameworkId frameworkId,
                                               EvidenceRecord evidence,
                                               LevelResolutionPolicy policy,
                                               Clock clock) {
        Objects.requireNonNull(competencyId, "competencyId must not be null");
        Objects.requireNonNull(frameworkId, "frameworkId must not be null");

        int existingIndex = indexOfAssertionFor(competencyId);
        if (existingIndex < 0) {
            ProficiencyAssertion opened =
                    ProficiencyAssertion.open(competencyId, frameworkId, evidence, policy, clock);
            assertions.add(opened);
            return opened;
        }

        ProficiencyAssertion existing = assertions.get(existingIndex);
        if (!existing.frameworkId().equals(frameworkId)) {
            throw new ConflictingFrameworkException(competencyId, existing.frameworkId(), frameworkId);
        }
        ProficiencyAssertion updated = existing.withAdditionalEvidence(evidence, policy, clock);
        assertions.set(existingIndex, updated);
        return updated;
    }

    /**
     * Whether this profile belongs to the given authenticated principal — the ownership predicate
     * the ADR-015 amendment relies on. A plain value comparison, because ADR-016 delivers the
     * caller's identity as data rather than as ambient state.
     */
    public boolean isOwnedBy(AuthSubject caller) {
        return authSubject.equals(caller);
    }

    public Optional<ProficiencyAssertion> assertionFor(CompetencyId competencyId) {
        int index = indexOfAssertionFor(competencyId);
        return index < 0 ? Optional.empty() : Optional.of(assertions.get(index));
    }

    public LearnerId id() { return id; }
    public AuthSubject authSubject() { return authSubject; }
    public DisplayName displayName() { return displayName; }
    public Instant createdAt() { return createdAt; }

    /** The profile's assertions. Unmodifiable: all mutation goes through {@link #recordEvidence}. */
    public List<ProficiencyAssertion> assertions() {
        return Collections.unmodifiableList(assertions);
    }

    private int indexOfAssertionFor(CompetencyId competencyId) {
        for (int i = 0; i < assertions.size(); i++) {
            if (assertions.get(i).competencyId().equals(competencyId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof LearnerProfile that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LearnerProfile[id=%s, assertions=%d]".formatted(id.value(), assertions.size());
    }
}
