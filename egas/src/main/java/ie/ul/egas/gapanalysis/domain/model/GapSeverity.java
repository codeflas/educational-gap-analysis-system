package ie.ul.egas.gapanalysis.domain.model;

/**
 * How serious a gap is judged to be — the output of a {@code GapSeverityPolicy}, never computed by
 * the aggregate itself (ADR-021).
 *
 * <p>The set is closed rather than a free scale so that a downstream consumer can branch on it
 * exhaustively. What each value <em>means</em> is the policy's business: the default reads them as
 * ordinal distance, and an institution weighting gaps by evidence confidence would assign the same
 * constants on entirely different grounds.
 *
 * <p>{@link #UNASSESSED} is the value that makes ADR-021's absent-attainment rule visible in the
 * output rather than only in the model. A learner with no evidence for a required competency is not
 * "very far behind" — nothing has been measured — and a recommender that conflated the two would
 * propose remediation where an assessment is what is actually needed.
 */
public enum GapSeverity {

    /** Attainment meets or exceeds the analysis target. */
    MET,

    /** Short of the target, but near it. */
    MINOR,

    /** Short of the target by a margin worth planning around. */
    MODERATE,

    /** Far short of the target. */
    MAJOR,

    /** Nothing has been measured: the learner holds no attainment for this competency. */
    UNASSESSED
}
