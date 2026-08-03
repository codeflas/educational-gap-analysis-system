package ie.ul.egas.gapanalysis.domain;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;

import java.util.Optional;

/**
 * Driven port for the compiled competency-model projection (ADR-007). Declared in the domain,
 * implemented by an adapter; the domain neither knows nor cares that the current adapter is JPA
 * over PostgreSQL.
 *
 * <p>There is no {@code delete} and no partial update. A projection is derived, so the only
 * meaningful write is "this framework now looks like <em>this</em>" — which is why {@link #project}
 * replaces rather than merges. That also makes redelivery harmless: Modulith's registry may deliver
 * an event more than once, and a replace-semantics write is idempotent where an append would
 * duplicate.
 */
public interface CompetencyModelProjectionRepository {

    /**
     * Records the projected state of one framework, replacing whatever was held for it before.
     *
     * <p>Replacement rather than merge is deliberate: the event carries the whole model, so a
     * partial update could only ever produce a projection that matches no version of the source.
     */
    void project(ProjectedCompetencyModel model);

    Optional<ProjectedCompetencyModel> findByFrameworkId(CompetencyFrameworkId frameworkId);

    boolean existsForFramework(CompetencyFrameworkId frameworkId);
}
