package ie.ul.egas.learner.api;

import java.util.List;

/**
 * Published read contract for a learner's resolved proficiency (ADR-022).
 *
 * <p><b>Synchronous, deliberately.</b> ADR-007 confines CQRS to the compiled competency-model
 * projection; attainment is not projected. The shapes differ: a competency model is a large,
 * rarely-changing graph, whereas attainment is small, per-learner, changes whenever evidence is
 * recorded, and is wanted fresh. Projecting it would trade immediate consistency for nothing —
 * and the module DAG already sanctions this call, since Gap Analysis declares
 * {@code allowedDependencies = {"competency :: api", "learner :: api"}}.
 *
 * <p>This is a read contract only. Nothing here mutates a profile, and provisioning and evidence
 * recording remain reachable solely through this context's own web adapter, where ownership is
 * enforced (ADR-015 Amendment 1).
 */
public interface LearnerAttainmentQuery {

    /**
     * Every competency this learner has a resolved assertion for, with the evidence behind each.
     *
     * <p>Returns empty for a learner with no profile. Absence is an ordinary outcome rather than an
     * error — a valid identity does not imply enrolment (ADR-017) — so a consumer computing gaps
     * treats an unenrolled learner as one who has attained nothing, which is exactly right.
     */
    List<AttainedCompetency> attainmentsFor(LearnerId learnerId);
}
