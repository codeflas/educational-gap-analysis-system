package ie.ul.egas.learner.domain.model;

/**
 * How much weight a piece of evidence carries, bounded to [0.0, 1.0] (ADR-018).
 *
 * <p>The bound is the point of the type. An unbounded numeric field makes "most confident"
 * meaningless as a resolution rule and lets one malformed submission dominate every subsequent
 * resolution for that competency. NaN and the infinities are rejected explicitly because they
 * pass a naive range check — {@code Double.NaN >= 0.0} is false, but so is
 * {@code Double.NaN > 1.0}, so a comparison-only guard admits them.
 */
public record Confidence(double value) implements Comparable<Confidence> {

    public static final Confidence CERTAIN = new Confidence(1.0);

    public Confidence {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Confidence must be a number, but was NaN");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("Confidence must be finite, but was " + value);
        }
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence must lie within [0.0, 1.0] but was " + value);
        }
    }

    @Override
    public int compareTo(Confidence other) {
        return Double.compare(value, other.value);
    }
}
