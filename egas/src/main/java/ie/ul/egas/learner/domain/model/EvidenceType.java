package ie.ul.egas.learner.domain.model;

/**
 * The kinds of observation that may support a proficiency claim (ADR-018).
 *
 * <p>Closed by design: an open string would let the input space of
 * {@link ie.ul.egas.learner.domain.policy.LevelResolutionPolicy} grow without the policy being
 * reconsidered. Adding a type is therefore a deliberate code change, which is the intended
 * friction.
 */
public enum EvidenceType {

    /** Asserted by the learner. Unverified by construction. */
    SELF_DECLARED,

    /** Produced by a formal assessment instrument. */
    ASSESSMENT,

    /** Inferred from completing a learning resource. */
    COURSE_COMPLETION,

    /** Attested by an external awarding body. */
    CERTIFICATION,

    /** Recorded by an educator or supervisor from direct observation. */
    OBSERVATION
}
