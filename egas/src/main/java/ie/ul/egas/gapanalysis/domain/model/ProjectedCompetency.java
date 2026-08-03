package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.competency.api.CompetencyId;

import java.util.List;
import java.util.Objects;

/**
 * One competency as Gap Analysis holds it, keyed by the identity Competency Modelling derives from
 * the framework and the code (ADR-019 Amendment 1).
 *
 * <p>That identity is the join key: a learner's assertion names a {@code CompetencyId}, and this
 * projection is what makes that name resolvable to something a framework defines. Before the
 * derivation existed the two sides could not have been matched at all.
 *
 * <p><b>{@code definedLevelCodes} is availability, not requirement.</b> It lists the levels for
 * which the source model carries a descriptor — what the framework says this competency means at
 * each level. The metamodel states no level anyone must reach, so a gap is measured against a
 * target supplied by the analysis request (ADR-021). A competency may define no levels at all,
 * which is a meaningful state and not a gap of zero.
 */
public record ProjectedCompetency(
        CompetencyId id,
        String code,
        String name,
        String areaCode,
        List<String> definedLevelCodes) {

    public ProjectedCompetency {
        Objects.requireNonNull(id, "competency id must not be null");
        Objects.requireNonNull(code, "competency code must not be null");
        definedLevelCodes = definedLevelCodes == null ? List.of() : List.copyOf(definedLevelCodes);
    }
}
