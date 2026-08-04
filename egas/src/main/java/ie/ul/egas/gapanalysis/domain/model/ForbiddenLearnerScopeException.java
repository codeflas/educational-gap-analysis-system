package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.learner.api.LearnerId;

/**
 * Raised when a caller asks for an operation scoped to a learner they are not, and may not act for.
 *
 * <p><b>Refusal here, not a 404, and the difference is principled.</b> ADR-015 Amendment 1 makes
 * denial indistinguishable from absence wherever a caller names an existing resource, because
 * "forbidden" would confirm the resource exists. That reasoning does not apply to these operations:
 * the learner identifier is supplied by the caller and never looked up — ADR-019 leaves
 * cross-context references unvalidated — so refusing discloses nothing whatsoever about whether
 * that learner exists, has a profile, or has any reports. Answering 404 instead would be a less
 * accurate diagnostic bought with no privacy gain.
 *
 * <p>Reading a report by its identifier is the opposite case, and raises
 * {@link GapReportNotFoundException} for it.
 */
public class ForbiddenLearnerScopeException extends RuntimeException {

    public ForbiddenLearnerScopeException(LearnerId requested) {
        super("The authenticated principal may not act for learner '%s'".formatted(requested.value()));
    }
}
