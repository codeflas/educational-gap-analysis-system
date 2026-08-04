package ie.ul.egas.gapanalysis.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full report representation: every finding, with what it was measured against, what the learner was
 * held to have attained, and the observations behind that.
 *
 * <p><b>The whole explainability chain is exposed, not summarised.</b> ADR-021 kept target,
 * attainment and provenance in the stored finding so a report could be defended rather than merely
 * displayed; a response that returned severity and a shortfall would discard exactly the data RQ3's
 * claim rests on, and no client could recover it.
 *
 * <p><b>Absence is rendered as absence.</b> With {@code default-property-inclusion: non_null}, an
 * unassessed finding omits {@code attainment} and {@code shortfall} entirely rather than sending
 * zeros — the wire form of the distinction the domain and the schema both protect. {@code unassessed}
 * states it positively as well, so a client need not infer meaning from a missing field.
 *
 * <p>{@code generatedAt} is not decoration. A report is a true record of its instant and stops
 * describing the present as soon as evidence changes; nothing invalidates it, so a client must read
 * this field as significant (ADR-021).
 */
public record GapReportResponse(
        UUID id,
        UUID learnerId,
        UUID frameworkId,
        Instant generatedAt,
        List<GapResponse> gaps) {

    public record GapResponse(
            UUID skillGapId,
            UUID competencyId,
            String competencyCode,
            String competencyName,
            String targetLevelCode,
            int targetOrdinal,
            String severity,
            boolean unassessed,
            Integer shortfall,
            AttainmentResponse attainment) {
    }

    /** Present only when something has been measured; omitted from the payload otherwise. */
    public record AttainmentResponse(
            int attainedOrdinal,
            String attainedLevelCode,
            Instant resolvedAt,
            List<EvidenceResponse> evidence) {
    }

    public record EvidenceResponse(
            String type,
            int claimedOrdinal,
            String claimedLevelCode,
            double confidence,
            String source,
            Instant recordedAt) {
    }
}
