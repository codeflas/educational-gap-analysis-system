package ie.ul.egas.learner.infrastructure.web;

import ie.ul.egas.learner.domain.model.ConflictingFrameworkException;
import ie.ul.egas.learner.domain.model.DuplicateLearnerProfileException;
import ie.ul.egas.learner.domain.model.LearnerProfileNotFoundException;
import ie.ul.egas.learner.domain.policy.UnresolvableEvidenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Module-local error mapping, scoped to this controller in the style the competency context
 * established — each adapter owns the rendering of its own failures.
 *
 * <p><b>The 404 body is a constant, on purpose.</b> {@link LearnerProfileNotFoundException} is
 * raised both when a profile does not exist and when it exists but the caller may not read it
 * (ADR-015 Amendment 1). Echoing {@code getMessage()} would let the two cases diverge — the
 * identifier-scoped and caller-scoped factories word themselves differently — and a client able to
 * tell them apart could enumerate learner identifiers by observing which answer it received. A
 * fixed body makes present-and-forbidden indistinguishable from absent.
 *
 * <p>Conflicting evidence is 422 rather than 400: the request was syntactically valid but describes
 * a competency under a framework the existing assertion does not belong to — a semantic failure,
 * the same distinction the competency context draws for non-conforming models.
 */
@RestControllerAdvice(assignableTypes = LearnerProfileController.class)
class LearnerProfileExceptionHandler {

    @ExceptionHandler(LearnerProfileNotFoundException.class)
    ProblemDetail onNotFound(LearnerProfileNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Learner profile not found");
        problem.setDetail("No learner profile is available for this request.");
        return problem;
    }

    @ExceptionHandler(DuplicateLearnerProfileException.class)
    ProblemDetail onDuplicate(DuplicateLearnerProfileException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Learner profile already exists");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(ConflictingFrameworkException.class)
    ProblemDetail onConflictingFramework(ConflictingFrameworkException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Evidence conflicts with the existing assertion");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(UnresolvableEvidenceException.class)
    ProblemDetail onUnresolvableEvidence(UnresolvableEvidenceException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Proficiency level could not be resolved");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    /** Value-object construction failures — a malformed level code, an out-of-range confidence. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onInvalidValue(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request value");
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
