package ie.ul.egas.learner.application;

/**
 * Request to provision a learner profile for the authenticated caller (ADR-017).
 *
 * <p>Primitives rather than value objects, mirroring {@code RegisterFrameworkCommand}: the command
 * is the application's input boundary, so the web adapter can build one without importing domain
 * types, and every value-object validation failure arises at a single predictable place — the
 * service — where the error advice can render it uniformly.
 *
 * <p><b>{@code authSubject} is not user input.</b> It carries the JWT {@code sub} claim, supplied
 * by the controller from the validated token and never read from a request body (ADR-016). A DTO
 * that offered this field would let a caller provision a profile for someone else, so no DTO does.
 */
public record CreateLearnerProfileCommand(String authSubject, String displayName) {
}
