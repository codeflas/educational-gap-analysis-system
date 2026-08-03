package ie.ul.egas.gapanalysis.domain.model;

import java.util.Objects;

/**
 * One position on a projected framework's proficiency scale.
 *
 * <p>Both {@code ordinal} and {@code code} are kept because they answer different questions — a gap
 * is measured in ordinals, while a target arrives from a request as a code and a report is read by
 * a human as one. Deriving either from the other would need the source model, which ADR-011 forbids
 * this context from reaching for.
 *
 * <p>Ordering is by ordinal and then by code. The second criterion is not decoration: ordering by
 * ordinal alone would make two distinct levels compare equal while {@code equals} reports them
 * different, breaking the {@link Comparable} contract — the same defect {@code AttainedLevel} was
 * corrected for in Step 4, avoided here by construction rather than by review.
 */
public record ProjectedLevel(String code, String name, int ordinal) implements Comparable<ProjectedLevel> {

    public ProjectedLevel {
        Objects.requireNonNull(code, "level code must not be null");
        code = code.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Level code must not be blank");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("Level ordinal must not be negative but was " + ordinal);
        }
    }

    @Override
    public int compareTo(ProjectedLevel other) {
        int byOrdinal = Integer.compare(ordinal, other.ordinal);
        return byOrdinal != 0 ? byOrdinal : code.compareTo(other.code);
    }
}
