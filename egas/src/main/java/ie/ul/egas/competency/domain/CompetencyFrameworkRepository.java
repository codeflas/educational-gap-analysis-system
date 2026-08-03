package ie.ul.egas.competency.domain;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkSummary;
import ie.ul.egas.competency.domain.model.FrameworkVersion;

import java.util.List;
import java.util.Optional;

/**
 * Driven port for framework model persistence (hexagonal architecture). Defined in the domain,
 * implemented by an adapter; the domain neither knows nor cares that the current adapter is
 * JPA + PostgreSQL jsonb. {@link #findAllSummaries()} exists so the read path can avoid model
 * hydration entirely (see {@link ie.ul.egas.competency.domain.model.FrameworkSummary}).
 */
public interface CompetencyFrameworkRepository {

    CompetencyFramework save(CompetencyFramework framework);

    Optional<CompetencyFramework> findById(CompetencyFrameworkId id);

    List<FrameworkSummary> findAllSummaries();

    boolean existsByNameAndVersion(FrameworkName name, FrameworkVersion version);
}
