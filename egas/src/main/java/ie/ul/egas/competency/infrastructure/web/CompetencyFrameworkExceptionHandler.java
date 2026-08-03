package ie.ul.egas.competency.infrastructure.web;

import ie.ul.egas.competency.domain.model.DuplicateFrameworkException;
import ie.ul.egas.competency.domain.model.FrameworkNotFoundException;
import ie.ul.egas.competency.domain.validation.ModelConformanceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Module-local error mapping (scoped to this controller — each context owns the rendering of
 * its domain exceptions; framework-raised errors are handled by Spring's RFC 9457 support,
 * enabled in configuration). Conformance failures use 422 Unprocessable Content: the request
 * was syntactically valid (not 400) but the model it describes violates the metamodel — and the
 * full violation list is attached as a machine-readable extension member.
 */
@RestControllerAdvice(assignableTypes = CompetencyFrameworkController.class)
class CompetencyFrameworkExceptionHandler {

    @ExceptionHandler(ModelConformanceException.class)
    ProblemDetail onNonConformingModel(ModelConformanceException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Model does not conform to the competency metamodel");
        problem.setDetail("The submitted framework model violates metamodel constraints; see 'violations'.");
        problem.setProperty("violations", exception.report().violations());
        return problem;
    }

    @ExceptionHandler(DuplicateFrameworkException.class)
    ProblemDetail onDuplicate(DuplicateFrameworkException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Framework already registered");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(FrameworkNotFoundException.class)
    ProblemDetail onNotFound(FrameworkNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Framework not found");
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
