package ie.ul.egas.learner.domain.model;

import ie.ul.egas.learner.api.LearnerId;

/**
 * Raised when a profile cannot be resolved.
 *
 * <p>Also raised, deliberately, when a caller is not permitted to see a profile that does exist:
 * per the ADR-015 amendment, a learner requesting another learner's profile receives 404 rather
 * than 403, so that present-and-forbidden is indistinguishable from absent and the endpoint cannot
 * be used to enumerate learner identifiers. The two factories below name the lookup that failed
 * without disclosing which case it was.
 */
public class LearnerProfileNotFoundException extends RuntimeException {

    private LearnerProfileNotFoundException(String message) {
        super(message);
    }

    public static LearnerProfileNotFoundException forId(LearnerId id) {
        return new LearnerProfileNotFoundException(
                "No learner profile is available with id '%s'".formatted(id.value()));
    }

    /**
     * For the {@code /me} paths, where the caller is the subject: naming it discloses nothing the
     * caller does not already know.
     */
    public static LearnerProfileNotFoundException forCaller() {
        return new LearnerProfileNotFoundException(
                "The authenticated principal has no learner profile; create one first");
    }
}
