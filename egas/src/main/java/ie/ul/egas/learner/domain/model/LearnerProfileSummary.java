package ie.ul.egas.learner.domain.model;

import ie.ul.egas.learner.api.LearnerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read model for profile listings: metadata only, no assertions and no evidence.
 *
 * <p>Its existence is what lets the listing path be satisfied from metadata columns plus one
 * count, without loading an assertion graph per row — the same column-only projection discipline
 * that satisfied the Step 2 performance gate for framework listings.
 */
public record LearnerProfileSummary(
        LearnerId id,
        DisplayName displayName,
        int assertionCount,
        Instant createdAt) {

    public LearnerProfileSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (assertionCount < 0) {
            throw new IllegalArgumentException(
                    "Assertion count must not be negative but was " + assertionCount);
        }
    }
}
