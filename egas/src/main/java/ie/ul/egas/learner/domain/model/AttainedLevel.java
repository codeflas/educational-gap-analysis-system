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
     * Orders by ordinal alone. Two levels from different frameworks are comparable only because
     * the caller has established they belong to the same scale; the type cannot enforce that, and
     * the owning assertion is what guarantees it in practice.
     */
    @Override
    public int compareTo(AttainedLevel other) {
        return Integer.compare(ordinal, other.ordinal);
    }
}
