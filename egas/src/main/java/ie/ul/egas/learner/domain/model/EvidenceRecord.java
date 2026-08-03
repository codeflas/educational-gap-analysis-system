package ie.ul.egas.learner.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One observation supporting a proficiency claim: what kind it was, what level it claimed, how
 * much weight it carries, where it came from, and when it was recorded (ADR-018).
 *
 * <p>A value object, and append-only: evidence is never modified after recording, only added
 * beside. That is what makes a profile an audit trail by construction, and what allows a skill gap
 * computed downstream to be explained by the observations that produced it rather than merely
 * reported — the property RQ3's explainability claim rests on.
 *
 * <p>{@code source} is free-text provenance — for example an SFIA self-assessment from March
 * 2026 — bounded in length and documented as not a place for personal data; the profile's GDPR
 * surface is deliberately confined to {@link DisplayName} and {@link AuthSubject}.
 */
public record EvidenceRecord(
        EvidenceType type,
        AttainedLevel claimedLevel,
        Confidence confidence,
        String source,
        Instant recordedAt) {

    private static final int MAX_SOURCE_LENGTH = 500;

    public EvidenceRecord {
        Objects.requireNonNull(type, "Evidence type must not be null");
        Objects.requireNonNull(claimedLevel, "Claimed level must not be null");
        Objects.requireNonNull(confidence, "Confidence must not be null");
        Objects.requireNonNull(source, "Evidence source must not be null");
        Objects.requireNonNull(recordedAt, "Evidence timestamp must not be null");
        source = source.trim();
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Evidence source must not be blank");
        }
        if (source.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException(
                    "Evidence source must not exceed " + MAX_SOURCE_LENGTH + " characters");
        }
    }
}
