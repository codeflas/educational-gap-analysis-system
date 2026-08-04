package ie.ul.egas.gapanalysis.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Listing representation: metadata and a count, no findings and no provenance.
 *
 * <p>The projection discipline reaching the wire. A learner's history could otherwise mean several
 * thousand rows serialised to render four columns, and the listing path never loads a gap graph in
 * the first place — this record is what makes that true end to end rather than only in the adapter.
 *
 * <p>{@code generatedAt} is what distinguishes one report from another in a history, which is why a
 * list ordered newest-first is the useful default.
 */
public record GapReportSummaryResponse(
        UUID id,
        UUID frameworkId,
        Instant generatedAt,
        int gapCount) {
}
