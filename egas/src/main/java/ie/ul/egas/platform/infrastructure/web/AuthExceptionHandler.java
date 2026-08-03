package ie.ul.egas.platform.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapping for the token endpoint, scoped to its controller in the module-local style the
 * competency context already uses — each adapter owns the rendering of its own failures.
 *
 * <p><b>The body is a constant, on purpose.</b> The exception's message is ignored rather than
 * echoed: an identical response for an unknown username and for a wrong password is the visible
 * half of the anti-enumeration guarantee, the timing half living in {@code TokenService}. A
 * handler that helpfully explained <em>which</em> half failed would undo both.
 *
 * <p>No {@code WWW-Authenticate: Bearer} challenge is emitted here. That header answers "your
 * bearer token was missing or bad" (RFC 6750) and belongs to the resource-server side; this
 * endpoint is where tokens are obtained, and no token was ever presented to it.
 */
@RestControllerAdvice(assignableTypes = AuthTokenController.class)
class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail onBadCredentials(BadCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Authentication failed");
        problem.setDetail("Invalid username or password.");
        return problem;
    }
}
