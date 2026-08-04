package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read model for report listings: metadata and a count, no gaps and no evidence.
 *
 * <p>Its existence is what lets a learner's report history be listed without hydrating every
 * finding and every observation behind it — the same column-only projection discipline that keeps
 * assertion graphs off the profile listing (ADR-020) and model hydration off the framework listing.
 * The saving is larger here than in either: a report holds one finding per competency in a
 * framework, each with its own provenance, so listing ten reports could otherwise mean loading
 * several thousand rows to display four columns.
 *
 * <p>No severity breakdown is carried. It would be useful and it is not needed yet; the count is
 * what distinguishes one report from another in a list, and the rest is a fetch away.
 */
public record GapReportSummary(
        GapReportId id,
        CompetencyFrameworkId frameworkId,
        Instant generatedAt,
        int gapCount) {

    public GapReportSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(frameworkId, "frameworkId must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        if (gapCount < 0) {
            throw new IllegalArgumentException(
                    "Gap count must not be negative but was " + gapCount);
        }
    }
}
