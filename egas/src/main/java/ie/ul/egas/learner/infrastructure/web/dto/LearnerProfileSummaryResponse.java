package ie.ul.egas.learner.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Listing response: metadata and a count only, never an assertion or evidence graph. */
public record LearnerProfileSummaryResponse(
        UUID id,
        String displayName,
        int assertionCount,
        Instant createdAt) {
}
