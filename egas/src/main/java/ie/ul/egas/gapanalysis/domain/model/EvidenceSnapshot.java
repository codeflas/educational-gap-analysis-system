package ie.ul.egas.gapanalysis.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One observation that supported a learner's attainment, copied into a report at the moment it was
 * computed — the last link of ADR-021's explainability chain.
 *
 * <p><b>Gap Analysis owns this type rather than reusing Learner Profiling's published
 * {@code EvidenceSummary}.</b> A stored report must remain explicable indefinitely, and reusing
 * another context's contract would make every historical report's shape hostage to that contract's
 * evolution. Copying the values costs a near-duplicate record and buys reports that cannot be
 * changed from outside — which is the whole point of ADR-021's snapshot rule.
 *
 * <p>{@code type} is a plain string because it arrives as one: Learner Profiling flattens its
 * {@code EvidenceType} enum at its own boundary, and re-typing it here would assert a shared
 * vocabulary these contexts deliberately do not have.
 */
public record EvidenceSnapshot(
        String type,
        int claimedOrdinal,
        String claimedLevelCode,
        double confidence,
        String source,
        Instant recordedAt) {

    public EvidenceSnapshot {
        Objects.requireNonNull(type, "evidence type must not be null");
        Objects.requireNonNull(claimedLevelCode, "claimed level code must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        if (claimedOrdinal < 0) {
            throw new IllegalArgumentException(
                    "Claimed ordinal must not be negative but was " + claimedOrdinal);
        }
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException(
                    "Confidence must lie within [0.0, 1.0] but was " + confidence);
        }
    }
}
