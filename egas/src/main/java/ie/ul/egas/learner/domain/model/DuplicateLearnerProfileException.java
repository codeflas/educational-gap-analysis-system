package ie.ul.egas.learner.domain.model;

/**
 * Raised when a profile already exists for the authenticated subject (ADR-017).
 *
 * <p>Thrown both by the application service's fast-path existence check and by the persistence
 * adapter when the {@code uq_learner_auth_subject} constraint fires — the check is a courtesy, the
 * constraint is the authority, and translating both to one exception closes the check-then-act
 * race exactly as framework registration does in Step 2.
 */
public class DuplicateLearnerProfileException extends RuntimeException {

    public DuplicateLearnerProfileException() {
        super("A learner profile already exists for the authenticated principal");
    }
}
