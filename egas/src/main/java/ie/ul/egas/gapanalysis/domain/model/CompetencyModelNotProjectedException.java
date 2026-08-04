package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;

/**
 * Raised when an analysis names a framework Gap Analysis holds no projection for.
 *
 * <p>The name states the fact rather than guessing at the cause, because there are two and they are
 * not distinguishable from here. The framework may not exist at all, or it may have been registered
 * moments ago and its projection not yet written — ADR-007 accepts that lag, and ADR-022 records
 * that registration is what triggers projection. Calling this "unknown framework" would assert the
 * first when the second is a legitimate and self-correcting state.
 *
 * <p>Reaching into Competency Modelling to tell the two apart is exactly what ADR-022 declined:
 * the projection exists so gap computation never makes that call.
 */
public class CompetencyModelNotProjectedException extends RuntimeException {

    public CompetencyModelNotProjectedException(CompetencyFrameworkId frameworkId) {
        super(("No competency model is available for framework '%s'. It may not exist, or its "
                + "projection may not have been written yet.").formatted(frameworkId.value()));
    }
}
