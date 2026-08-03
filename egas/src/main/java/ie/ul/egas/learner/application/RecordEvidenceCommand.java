package ie.ul.egas.learner.application;

import ie.ul.egas.learner.domain.model.EvidenceType;

import java.util.UUID;

/**
 * Request to record one observation against a competency on the caller's own profile (ADR-018).
 *
 * <p><b>Self-recording only.</b> There is no target-learner field: the profile written to is the
 * one belonging to {@code authSubject}, so ownership is implicit in the lookup rather than a
 * separate check. {@link EvidenceType#OBSERVATION} anticipates an educator recording evidence
 * about someone else, and that capability is deliberately deferred — adding it means a target
 * learner plus a {@code callerMayWriteAny} decision resolved in the security layer, which is a
 * change to make deliberately rather than by widening this record.
 *
 * <p><b>No timestamp field.</b> {@code recordedAt} is assigned by the service from the injected
 * {@code Clock}. A caller-supplied value would be unverifiable and would let a caller manipulate
 * the recency tie-break in {@code HighestConfidenceResolutionPolicy} to raise their own resolved
 * proficiency. Should the date an observation actually <em>occurred</em> ever be needed, it
 * arrives as a separate {@code observedAt} field rather than by redefining this one.
 *
 * <p>{@code competencyId} and {@code competencyFrameworkId} are carried as raw {@link UUID}s and
 * are never resolved against the Competency Modelling context (ADR-019).
 */
public record RecordEvidenceCommand(
        String authSubject,
        UUID competencyId,
        UUID competencyFrameworkId,
        EvidenceType type,
        int claimedOrdinal,
        String claimedLevelCode,
        double confidence,
        String source) {
}
