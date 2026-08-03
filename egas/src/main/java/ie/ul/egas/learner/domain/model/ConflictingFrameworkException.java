package ie.ul.egas.learner.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;

/**
 * Raised when evidence is recorded for a competency under a different framework than the one the
 * existing assertion was opened with (ADR-018).
 *
 * <p>A competency is contained by exactly one framework model, so the two identifiers are not
 * independent: a mismatch means the caller is confused about which model it is describing.
 * Accepting it silently would leave an assertion whose stored framework no longer explains its own
 * level codes. This context cannot check the pairing against Competency Modelling — ADR-019
 * records why references are unvalidated — but it can and does enforce internal consistency across
 * an assertion's lifetime.
 */
public class ConflictingFrameworkException extends RuntimeException {

    public ConflictingFrameworkException(CompetencyId competencyId,
                                         CompetencyFrameworkId expected,
                                         CompetencyFrameworkId supplied) {
        super(("Competency '%s' is already recorded under framework '%s'; evidence supplied "
                + "framework '%s'. A competency belongs to exactly one framework.")
                .formatted(competencyId.value(), expected.value(), supplied.value()));
    }
}
