package ie.ul.egas.gapanalysis.domain.model;

/**
 * Raised when an analysis requests a target level the framework does not define.
 *
 * <p>Refused rather than ignored. Silently skipping the competency would drop it from the report,
 * and a caller who mistyped a level code would receive a shorter report with no indication that
 * anything was missing — the failure mode hardest to notice and hardest to explain afterwards.
 *
 * <p>The check is against the <em>framework's</em> levels, not the competency's. A target is what
 * the analysis asks for, not what the model demands (ADR-021), so requesting L4 for a competency
 * whose descriptors stop at L2 is legitimate: the framework must define L4 for an ordinal to exist,
 * and nothing requires the competency to describe what it means there.
 */
public class UnknownTargetLevelException extends RuntimeException {

    public UnknownTargetLevelException(String competencyCode, String levelCode) {
        super(("Target level '%s' requested for competency '%s' is not defined by this framework")
                .formatted(levelCode, competencyCode));
    }
}
