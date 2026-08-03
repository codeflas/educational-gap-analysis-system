package ie.ul.egas.learner.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What a learner is held to have attained for one competency, and the evidence that supports it.
 *
 * <p>An entity inside the {@link LearnerProfile} aggregate — it has identity that outlives any
 * particular version of its state, but it is never addressed from outside the profile that
 * contains it. That is why {@link AssertionId} lives in {@code domain.model} rather than the
 * published {@code api} package.
 *
 * <p><b>Identity with immutable state.</b> Recording further evidence returns a <em>new</em>
 * instance carrying the same {@link AssertionId}, rather than mutating this one. Equality is by
 * identifier, so the aggregate can replace an element in place and every reference remains valid,
 * while no caller holding an assertion can observe it change underneath them. The alternative —
 * in-place mutation of a shared list of evidence — makes the resolved level and the evidence set
 * transiently inconsistent during an update, which is precisely the window in which a concurrent
 * reader would see a level unsupported by any evidence.
 *
 * <p><b>Why the framework travels with the assertion.</b> Proficiency levels are M1 elements
 * scoped to one framework model, so {@code L2} means nothing without knowing which framework
 * defined it. ADR-011 forbids reaching across the schema boundary to recover that, so
 * {@link CompetencyFrameworkId} is denormalised into this context (ADR-018). Once opened, an
 * assertion's framework is fixed: evidence naming a different one is rejected by
 * {@link LearnerProfile}, because a competency belongs to exactly one framework and a mismatch
 * means the caller is describing a different model than it believes.
 */
public final class ProficiencyAssertion {

    private final AssertionId id;
    private final CompetencyId competencyId;
    private final CompetencyFrameworkId frameworkId;
    private final AttainedLevel attainedLevel;
    private final List<EvidenceRecord> evidence;
    private final Instant resolvedAt;

    private ProficiencyAssertion(AssertionId id, CompetencyId competencyId,
                                 CompetencyFrameworkId frameworkId, AttainedLevel attainedLevel,
                                 List<EvidenceRecord> evidence, Instant resolvedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.competencyId = Objects.requireNonNull(competencyId, "competencyId must not be null");
        this.frameworkId = Objects.requireNonNull(frameworkId, "frameworkId must not be null");
        this.attainedLevel = Objects.requireNonNull(attainedLevel, "attainedLevel must not be null");
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        if (this.evidence.isEmpty()) {
            throw new IllegalArgumentException("A proficiency assertion requires at least one evidence record");
        }
    }

    /**
     * Opens an assertion from its first piece of evidence, resolving the attained level through
     * the supplied policy.
     */
    static ProficiencyAssertion open(CompetencyId competencyId, CompetencyFrameworkId frameworkId,
                                     EvidenceRecord firstEvidence, LevelResolutionPolicy policy,
                                     Clock clock) {
        Objects.requireNonNull(firstEvidence, "firstEvidence must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        List<EvidenceRecord> evidence = List.of(firstEvidence);
        return new ProficiencyAssertion(
                AssertionId.random(), competencyId, frameworkId,
                policy.resolve(evidence), evidence, clock.instant());
    }

    /**
     * Rehydrates a previously persisted assertion. The store holds only assertions that were
     * resolved by a policy when written, so no re-resolution occurs here — re-resolving on load
     * would silently rewrite history whenever the configured policy changed, which ADR-018 records
     * as a deliberate non-behaviour.
     */
    public static ProficiencyAssertion reconstitute(AssertionId id, CompetencyId competencyId,
                                                    CompetencyFrameworkId frameworkId,
                                                    AttainedLevel attainedLevel,
                                                    List<EvidenceRecord> evidence,
                                                    Instant resolvedAt) {
        return new ProficiencyAssertion(id, competencyId, frameworkId, attainedLevel, evidence, resolvedAt);
    }

    /**
     * Returns a new assertion — same identity — carrying the additional evidence and the level
     * re-resolved across the whole set.
     */
    ProficiencyAssertion withAdditionalEvidence(EvidenceRecord additional, LevelResolutionPolicy policy,
                                                Clock clock) {
        Objects.requireNonNull(additional, "additional evidence must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        List<EvidenceRecord> combined = new ArrayList<>(evidence);
        combined.add(additional);
        return new ProficiencyAssertion(
                id, competencyId, frameworkId, policy.resolve(combined), combined, clock.instant());
    }

    public AssertionId id() { return id; }
    public CompetencyId competencyId() { return competencyId; }
    public CompetencyFrameworkId frameworkId() { return frameworkId; }
    public AttainedLevel attainedLevel() { return attainedLevel; }
    public Instant resolvedAt() { return resolvedAt; }

    /** The supporting evidence, oldest first. Unmodifiable: evidence is append-only (ADR-018). */
    public List<EvidenceRecord> evidence() {
        return Collections.unmodifiableList(evidence);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof ProficiencyAssertion that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ProficiencyAssertion[competency=%s, level=%s, evidence=%d]"
                .formatted(competencyId.value(), attainedLevel.code(), evidence.size());
    }
}
