package ie.ul.egas.gapanalysis.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a learner was held to have attained for one competency at the moment a report was computed,
 * together with the observations that supported it — the middle link of ADR-021's explainability
 * chain.
 *
 * <p><b>Its absence is modelled by absence, not by a value.</b> There is no "unattained" instance
 * of this type and no zero-ordinal sentinel: a competency the learner has no evidence for is
 * represented by {@link SkillGap} holding no snapshot at all. Collapsing "never assessed" into
 * "assessed at the lowest level" would erase the distinction most useful to a recommender, which is
 * why ADR-021 makes absence first-class.
 *
 * <p>Evidence is carried rather than counted. A report that recorded only a resolved level could be
 * displayed but not defended, and RQ3's explainability claim would rest on prose instead of data.
 */
public record AttainmentSnapshot(
        int attainedOrdinal,
        String attainedLevelCode,
        Instant resolvedAt,
        List<EvidenceSnapshot> evidence) {

    public AttainmentSnapshot {
        Objects.requireNonNull(attainedLevelCode, "attainedLevelCode must not be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        attainedLevelCode = attainedLevelCode.trim();
        if (attainedLevelCode.isEmpty()) {
            throw new IllegalArgumentException("Attained level code must not be blank");
        }
        if (attainedOrdinal < 0) {
            throw new IllegalArgumentException(
                    "Attained ordinal must not be negative but was " + attainedOrdinal);
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
