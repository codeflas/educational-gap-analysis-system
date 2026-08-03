package ie.ul.egas.gapanalysis.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data internals of the projection adapter — invisible outside this package.
 *
 * <p>Levels are fetched with the framework because they are never wanted without it; competencies
 * are loaded separately, since joining two collections in one query produces a cartesian product
 * of levels and competencies.
 */
interface ProjectedFrameworkSpringDataRepository extends JpaRepository<ProjectedFrameworkJpaEntity, UUID> {

    @EntityGraph(attributePaths = "levels")
    Optional<ProjectedFrameworkJpaEntity> findWithLevelsByFrameworkId(UUID frameworkId);

    boolean existsByFrameworkId(UUID frameworkId);

    /**
     * Removes a framework's projection. The schema cascades to levels, competencies and their level
     * codes, which is what makes replacing a projection a single statement rather than a
     * choreography.
     */
    void deleteByFrameworkId(UUID frameworkId);
}
