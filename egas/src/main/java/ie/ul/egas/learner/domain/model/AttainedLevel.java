package ie.ul.egas.learner.domain.model;

import java.util.Objects;

/**
 * A position on a competency framework's proficiency scale: the comparable {@code ordinal} and
 * the framework-scoped {@code code} that names it.
 *
 * <p>Both are retained because they answer different questions. Gap Analysis compares ordinals to
 * compute a gap; humans and API clients read codes. Neither alone is sufficient, and deriving one
 * from the other would require the competency model, which ADR-011 forbids this context from
 * reaching across the schema boundary to obtain.
 *
 * <p>Levels are only meaningful within their framework — {@code L2} in a bespoke curriculum
 * framework and {@code L2} in SFIA are unrelated — so ordering is defined here but the framework
 * context is carried by the owning {@link ProficiencyAssertion} (ADR-018).
 */
public record AttainedLevel(int ordinal, String code) implements Comparable<AttainedLevel> {

    private static final int MAX_CODE_LENGTH = 50;

    public AttainedLevel {
        Objects.requireNonNull(code, "Level code must not be null");
        code = code.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Level code must not be blank");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException(
                    "Level code must not exceed " + MAX_CODE_LENGTH + " characters");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("Level ordinal must not be negative but was " + ordinal);
        }
    }

    /**
     * Orders by ordinal — the only comparison that carries proficiency meaning — and then by code
     * as a tie-break that carries none.
     *
     * <p><b>Why the second criterion exists.</b> Ordinal alone would make {@code (2, "L2")} and
     * {@code (2, "SFIA-2")} compare as equal while {@link #equals} reports them different,
     * breaking the {@link Comparable} contract's consistency recommendation. Two concrete failures
     * follow from that, and both are prevented here rather than documented as caveats: a
     * {@code TreeSet} or {@code TreeMap} would silently collapse the two distinct levels into one,
     * and — the live case — {@code HighestConfidenceResolutionPolicy} chains this ordering as its
     * final tie-break, so evidence differing only in level code would compare as tied and the
     * resolved level would depend on the order the evidence happened to be stored in. ADR-018
     * requires resolution to be deterministic for any input; that guarantee rests on this order
     * being total over the value space, not merely over ordinals.
     *
     * <p>Lexicographic code ordering asserts nothing about proficiency: it is an arbitrary but
     * stable choice, and the only property required of it is that it separates values {@code equals}
     * separates. Two levels from different frameworks remain comparable only because the caller has
     * established they belong to the same scale; the type cannot enforce that, and the owning
     * {@link ProficiencyAssertion} is what guarantees it in practice.
     */
    @Override
    public int compareTo(AttainedLevel other) {
        int byOrdinal = Integer.compare(ordinal, other.ordinal);
        return byOrdinal != 0 ? byOrdinal : code.compareTo(other.code);
    }
}
