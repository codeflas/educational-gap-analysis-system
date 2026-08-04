package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.competency.api.CompetencyId;

import java.util.Objects;

/**
 * What one competency was measured against in a particular analysis — the first link of ADR-021's
 * explainability chain.
 *
 * <p><b>A target, not a requirement.</b> Nothing in a competency model says a learner ought to
 * reach a given level: the M2 metamodel's {@code LevelDescriptor} states what a level <em>means</em>
 * for a competency and never what is demanded of anyone. The level here was supplied by the
 * analysis request (or defaulted to the highest the framework defines), which is why it must be
 * recorded per gap: the same attainment yields a different gap under a different target, so a
 * stored finding that omitted its target could not be defended later.
 *
 * <p><b>A snapshot, not a reference.</b> The competency's code and name are copied rather than
 * pointed at, so a report computed in March stays explicable in June after the framework has been
 * revised (ADR-021). {@code competencyId} is retained as well — it is the derived identity
 * (ADR-019 Amendment 1) that ties this finding back to a live competency when one still exists.
 */
public record AnalysisTarget(
        CompetencyId competencyId,
        String competencyCode,
        String competencyName,
        String targetLevelCode,
        int targetOrdinal) {

    public AnalysisTarget {
        Objects.requireNonNull(competencyId, "competencyId must not be null");
        Objects.requireNonNull(competencyCode, "competencyCode must not be null");
        Objects.requireNonNull(targetLevelCode, "targetLevelCode must not be null");
        competencyCode = competencyCode.trim();
        targetLevelCode = targetLevelCode.trim();
        if (competencyCode.isEmpty()) {
            throw new IllegalArgumentException("Competency code must not be blank");
        }
        if (targetLevelCode.isEmpty()) {
            throw new IllegalArgumentException("Target level code must not be blank");
        }
        if (targetOrdinal < 0) {
            throw new IllegalArgumentException(
                    "Target ordinal must not be negative but was " + targetOrdinal);
        }
    }
}
