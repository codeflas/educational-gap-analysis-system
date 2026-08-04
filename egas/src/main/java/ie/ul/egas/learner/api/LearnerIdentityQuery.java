package ie.ul.egas.learner.api;

import java.util.Optional;

/**
 * Published read contract resolving an authenticated principal to the learner it identifies
 * (ADR-017, ADR-022 Amendment 1).
 *
 * <p><b>Why this must exist.</b> ADR-015 Amendment 1 puts ownership enforcement in the application
 * layer, evaluated against the loaded resource, with the caller's subject arriving as an explicit
 * command field (ADR-016). Learner Profiling can do that unaided because {@code LearnerProfile}
 * holds the subject and answers {@code isOwnedBy}. A consumer holding only a {@link LearnerId} —
 * Gap Analysis, whose reports carry a learner reference and nothing else by ADR-021's design —
 * cannot, because {@code AuthSubject} and {@code findByAuthSubject} live in {@code learner.domain},
 * which the module DAG puts out of reach. Without this contract, ownership for any resource owned
 * by a learner but stored by another context is inexpressible.
 *
 * <p><b>The mapping does not move.</b> ADR-017 decided the correspondence is resolved inside
 * Learner Profiling rather than derived from the subject or held by the security layer. That is
 * unchanged: this publishes the <em>resolution</em>, not the mapping. No consumer learns how a
 * subject relates to a profile, no consumer stores one, and the identity provider stays substitutable
 * exactly as ADR-013 anticipates.
 *
 * <p>{@code authSubject} is a plain string because it arrives as one. {@code AuthSubject} is a
 * domain type and {@code publishedContractsArePure} forbids one here — the same flattening
 * {@link AttainedCompetency} applies to {@code EvidenceType}, and the same opacity ADR-016 relies on
 * when it makes identity travel as data.
 *
 * <p><b>Consumers must pass the caller's own subject, taken from the token.</b> ADR-016 already
 * states that a subject may never be read from a request body; that rule is what keeps this from
 * becoming an oracle mapping arbitrary principals to learner identifiers.
 */
public interface LearnerIdentityQuery {

    /**
     * The learner this principal identifies, empty when the principal has no profile.
     *
     * <p>Absence is an ordinary outcome, not an error: provisioning is an explicit act and a valid
     * token does not imply enrolment (ADR-017). A consumer enforcing ownership treats an unenrolled
     * caller as owning nothing, which is exactly right.
     *
     * <p>Subjects are stored trimmed, so the raw {@code sub} claim matches as given; a value that
     * matches no profile yields empty rather than an exception, because "no such learner" and "not
     * enrolled yet" are the same answer to this question.
     */
    Optional<LearnerId> learnerIdFor(String authSubject);
}
