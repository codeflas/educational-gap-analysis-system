package ie.ul.egas.gapanalysis.infrastructure.web;

import ie.ul.egas.gapanalysis.domain.model.CompetencyModelNotProjectedException;
import ie.ul.egas.gapanalysis.domain.model.ForbiddenLearnerScopeException;
import ie.ul.egas.gapanalysis.domain.model.GapReportNotFoundException;
import ie.ul.egas.gapanalysis.domain.model.UnknownTargetLevelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Module-local error mapping, scoped to this controller in the style the competency and learner
 * adapters established — each adapter owns the rendering of its own failures.
 *
 * <p><b>The 404 body is a constant, on purpose.</b> {@link GapReportNotFoundException} is raised
 * both when a report does not exist and when it exists but the caller may not read it (ADR-015
 * Amendment 2). Echoing {@code getMessage()} would let the two cases diverge, and a client able to
 * tell them apart could enumerate report identifiers — worse here than for a profile, since a report
 * names the learner it is about.
 *
 * <p><b>403 where 404 would be theatre.</b> {@link ForbiddenLearnerScopeException} carries a real
 * message, because the operations that raise it are scoped by a learner identifier the caller
 * supplied and the system never looked up. Nothing about that learner is disclosed by refusing, so
 * hiding the refusal behind a 404 would cost the caller a usable diagnostic and buy no privacy.
 *
 * <p>An unavailable competency model is 422 rather than 404 or 400: the request is well formed and
 * names a framework that cannot be analysed <em>right now</em> — it may not exist, or its projection
 * may not have arrived yet (ADR-007's accepted lag), and this layer cannot tell which without the
 * cross-context call ADR-022 declined to make. A 404 would assert the first; 422 states the fact
 * without guessing, and the detail names both possibilities so a caller can decide whether to retry.
 */
@RestControllerAdvice(assignableTypes = GapReportController.class)
class GapAnalysisExceptionHandler {

    @ExceptionHandler(GapReportNotFoundException.class)
    ProblemDetail onNotFound(GapReportNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Gap report not found");
        problem.setDetail("No gap report is available for this request.");
        return problem;
    }

    @ExceptionHandler(ForbiddenLearnerScopeException.class)
    ProblemDetail onForbiddenScope(ForbiddenLearnerScopeException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Not permitted for this learner");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(CompetencyModelNotProjectedException.class)
    ProblemDetail onModelUnavailable(CompetencyModelNotProjectedException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Competency model unavailable");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(UnknownTargetLevelException.class)
    ProblemDetail onUnknownTargetLevel(UnknownTargetLevelException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Unknown target level");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    /** Value-object construction failures — a blank competency code, a negative ordinal. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onInvalidValue(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request value");
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
