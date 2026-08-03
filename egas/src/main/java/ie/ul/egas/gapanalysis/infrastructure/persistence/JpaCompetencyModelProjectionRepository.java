package ie.ul.egas.gapanalysis.infrastructure.persistence;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter for the compiled competency-model projection (ADR-007).
 *
 * <p><b>Writing is replacement, and that is the whole design.</b> The event carries an entire
 * model, so the only coherent write is to delete whatever was held for that framework and insert
 * what arrived. Two consequences follow, both wanted: redelivery is harmless, which matters because
 * Modulith's registry may deliver an event more than once and an appending write would duplicate;
 * and the projection can never drift into a state that matches no version of the source, which a
 * partial update could produce.
 *
 * <p>The delete is flushed before the insert. Hibernate orders inserts before deletes within a
 * flush, so without the explicit boundary a replacement would collide with the rows it was about to
 * remove on the framework's primary key — the same ordering hazard evidence rows hit in Step 4,
 * resolved there by deriving identity and here by sequencing the statements.
 */
@Repository
class JpaCompetencyModelProjectionRepository implements CompetencyModelProjectionRepository {

    private final ProjectedFrameworkSpringDataRepository frameworks;
    private final ProjectedCompetencySpringDataRepository competencies;

    @PersistenceContext
    private EntityManager entityManager;

    JpaCompetencyModelProjectionRepository(ProjectedFrameworkSpringDataRepository frameworks,
                                           ProjectedCompetencySpringDataRepository competencies) {
        this.frameworks = frameworks;
        this.competencies = competencies;
    }

    @Override
    @Transactional
    public void project(ProjectedCompetencyModel model) {
        frameworks.deleteByFrameworkId(model.frameworkId().value());
        // Deletes must reach the database before the replacement rows are inserted; Hibernate would
        // otherwise order the inserts first and violate the primary key.
        entityManager.flush();

        frameworks.save(toEntity(model));
        competencies.saveAll(model.competencies().stream()
                .map(competency -> toEntity(competency, model))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectedCompetencyModel> findByFrameworkId(CompetencyFrameworkId frameworkId) {
        return frameworks.findWithLevelsByFrameworkId(frameworkId.value())
                .map(framework -> toDomain(framework,
                        competencies.findByFrameworkIdOrderByCodeAsc(frameworkId.value())));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForFramework(CompetencyFrameworkId frameworkId) {
        return frameworks.existsByFrameworkId(frameworkId.value());
    }

    private ProjectedFrameworkJpaEntity toEntity(ProjectedCompetencyModel model) {
        ProjectedFrameworkJpaEntity entity = new ProjectedFrameworkJpaEntity(
                model.frameworkId().value(),
                model.frameworkName(),
                model.frameworkVersion(),
                model.registeredAt(),
                model.projectedAt());
        model.levels().forEach(level -> entity.addLevel(
                new ProjectedLevelEmbeddable(level.code(), level.name(), level.ordinal())));
        return entity;
    }

    private ProjectedCompetencyJpaEntity toEntity(ProjectedCompetency competency,
                                                   ProjectedCompetencyModel model) {
        ProjectedCompetencyJpaEntity entity = new ProjectedCompetencyJpaEntity(
                competency.id().value(),
                model.frameworkId().value(),
                competency.code(),
                competency.name(),
                competency.areaCode());
        competency.definedLevelCodes().forEach(entity::addDefinedLevelCode);
        return entity;
    }

    private ProjectedCompetencyModel toDomain(ProjectedFrameworkJpaEntity framework,
                                              List<ProjectedCompetencyJpaEntity> competencyEntities) {
        return new ProjectedCompetencyModel(
                new CompetencyFrameworkId(framework.getFrameworkId()),
                framework.getName(),
                framework.getVersion(),
                framework.getRegisteredAt(),
                framework.getProjectedAt(),
                framework.getLevels().stream()
                        .map(level -> new ProjectedLevel(level.getCode(), level.getName(), level.getOrdinal()))
                        .toList(),
                competencyEntities.stream().map(this::toDomain).toList());
    }

    private ProjectedCompetency toDomain(ProjectedCompetencyJpaEntity entity) {
        return new ProjectedCompetency(
                new CompetencyId(entity.getCompetencyId()),
                entity.getCode(),
                entity.getName(),
                entity.getAreaCode(),
                List.copyOf(entity.getDefinedLevelCodes()));
    }
}
