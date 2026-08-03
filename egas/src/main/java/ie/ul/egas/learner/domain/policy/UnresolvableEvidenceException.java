package ie.ul.egas.learner.domain.policy;

/**
 * Raised when a {@link LevelResolutionPolicy} cannot determine a level from the evidence it was
 * given — an empty set under the default policy, or a set failing a stricter policy's threshold
 * (a corroboration rule, for instance, might refuse a lone self-declaration).
 *
 * <p>Distinct from a malformed request: the input was well-formed and the policy declined to draw
 * a conclusion from it, which the web adapter renders as 422 rather than 400.
 */
public class UnresolvableEvidenceException extends RuntimeException {

    public UnresolvableEvidenceException(String message) {
        super(message);
    }
}
