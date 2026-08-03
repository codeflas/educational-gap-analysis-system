package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A competency model as Gap Analysis holds it: flattened, queryable, and derived (ADR-007).
 *
 * <p>This is <b>this context's</b> read model, not Competency Modelling's. It is built from the
 * published snapshot and shaped for the question gap computation asks — what does this framework
 * define, and at what levels — rather than for the question the source model answers, which is what
 * a competency <em>means</em>. The two happen to look similar today; keeping them separate types is
 * what allows either to change without dragging the other.
 *
 * <p>Being derived is the important property. Nothing here is authored, the source of truth stays
 * in the owning context, and a defect is repaired by replaying the event rather than by migrating
 * data. That is what makes ADR-007's eventual consistency an acceptable trade rather than a risk.
 *
 * <p>{@code registeredAt} is when the source model was registered; {@code projectedAt} is when this
 * projection was written. Both are kept so projection lag is observable rather than inferred.
 */
public record ProjectedCompetencyModel(
        CompetencyFrameworkId frameworkId,
        String frameworkName,
        String frameworkVersion,
        Instant registeredAt,
        Instant projectedAt,
        List<ProjectedLevel> levels,
        List<ProjectedCompetency> competencies) {

    public ProjectedCompetencyModel {
        Objects.requireNonNull(frameworkId, "frameworkId must not be null");
        Objects.requireNonNull(frameworkName, "frameworkName must not be null");
        Objects.requireNonNull(frameworkVersion, "frameworkVersion must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        levels = levels == null ? List.of() : List.copyOf(levels);
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
    }

    /**
     * The level bearing this code, if the framework defines one.
     *
     * <p>Resolving a level code to its ordinal is the projection's reason for holding levels at
     * all: an analysis target arrives as a code, and a gap is measured in ordinals.
     */
    public Optional<ProjectedLevel> levelByCode(String code) {
        return levels.stream().filter(level -> level.code().equals(code)).findFirst();
    }

    /** The highest level this framework defines, if it defines any. */
    public Optional<ProjectedLevel> highestLevel() {
        return levels.stream().max(ProjectedLevel::compareTo);
    }
}
