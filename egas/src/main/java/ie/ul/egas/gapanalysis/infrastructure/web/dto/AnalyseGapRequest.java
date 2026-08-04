package ie.ul.egas.gapanalysis.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Request to compute a learner's gaps against one framework.
 *
 * <p><b>{@code learnerId} is present where {@code RecordEvidenceRequest} has no subject field, and
 * the difference is deliberate.</b> Recording evidence is self-only, so naming a learner would be a
 * way to write to someone else's profile. Analysing is not self-only — an educator analysing a
 * student is a legitimate case — so the learner is named, and the caller's own identity still
 * arrives separately from the validated token (ADR-016). A caller naming a learner they are not
 * receives 403; supplying the field buys them nothing.
 *
 * <p><b>{@code targetLevelCodes} maps competency code to the level this analysis measures against.</b>
 * Omitting it, or omitting a competency from it, measures against the highest level for which that
 * competency has a descriptor — a stated default, not a requirement discovered in the model, since
 * the metamodel states none (ADR-021). Keys are codes rather than identifiers because a caller
 * composing an analysis holds the framework's vocabulary, not derived UUIDs.
 *
 * <p>Validation here is transport-tier only: a level code the framework does not define is a
 * semantic question the application layer answers, because only it holds the projected model.
 */
public record AnalyseGapRequest(
        @NotNull UUID learnerId,
        @NotNull UUID frameworkId,
        @Size(max = 500) Map<String, String> targetLevelCodes) {
}
