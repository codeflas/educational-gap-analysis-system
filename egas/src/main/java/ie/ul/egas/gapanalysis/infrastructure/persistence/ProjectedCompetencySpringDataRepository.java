package ie.ul.egas.gapanalysis.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data internals of the projection adapter — invisible outside this package. */
interface ProjectedCompetencySpringDataRepository extends JpaRepository<ProjectedCompetencyJpaEntity, UUID> {

    /**
     * Ordered by code so a projection reads back deterministically. The order carries no meaning of
     * its own; determinism is what makes a round-trip assertion possible without sorting at every
     * call site.
     */
    List<ProjectedCompetencyJpaEntity> findByFrameworkIdOrderByCodeAsc(UUID frameworkId);
}
