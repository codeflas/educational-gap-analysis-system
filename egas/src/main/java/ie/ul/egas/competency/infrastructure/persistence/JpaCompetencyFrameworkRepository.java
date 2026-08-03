package ie.ul.egas.competency.infrastructure.persistence;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.domain.CompetencyFrameworkRepository;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.DuplicateFrameworkException;
import ie.ul.egas.competency.domain.model.FrameworkDescriptor;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkSummary;
import ie.ul.egas.competency.domain.model.FrameworkVersion;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter implementing the domain port. Maps aggregate ↔ mapping entity, delegates
 * SQL to Spring Data, and — crucially — translates infrastructure exceptions into domain
 * exceptions at the boundary: the unique-constraint violation becomes
 * {@link DuplicateFrameworkException}, making the database the authoritative, race-free guard
 * behind the service's fast-path check. {@code saveAndFlush} surfaces the violation inside this
 * method rather than at an arbitrary later flush point.
 */
@Repository
class JpaCompetencyFrameworkRepository implements CompetencyFrameworkRepository {

    private final FrameworkModelSpringDataRepository springData;
    private final EmfJsonModelSerializer serializer;

    JpaCompetencyFrameworkRepository(FrameworkModelSpringDataRepository springData,
                                     EmfJsonModelSerializer serializer) {
        this.springData = springData;
        this.serializer = serializer;
    }

    @Override
    public CompetencyFramework save(CompetencyFramework framework) {
        try {
            springData.saveAndFlush(toEntity(framework));
            return framework;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFrameworkException(
                    framework.descriptor().name(), framework.descriptor().version());
        }
    }

    @Override
    public Optional<CompetencyFramework> findById(CompetencyFrameworkId id) {
        return springData.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<FrameworkSummary> findAllSummaries() {
        return springData.findAllByOrderByRegisteredAtDesc().stream()
                .map(view -> new FrameworkSummary(
                        new CompetencyFrameworkId(view.getId()),
                        new FrameworkName(view.getName()),
                        new FrameworkVersion(view.getVersion()),
                        view.getSource(),
                        view.getStatus(),
                        view.getRegisteredAt()))
                .toList();
    }

    @Override
    public boolean existsByNameAndVersion(FrameworkName name, FrameworkVersion version) {
        return springData.existsByNameAndVersion(name.value(), version.value());
    }

    private FrameworkModelJpaEntity toEntity(CompetencyFramework framework) {
        FrameworkDescriptor descriptor = framework.descriptor();
        return new FrameworkModelJpaEntity(
                framework.id().value(),
                descriptor.name().value(),
                descriptor.version().value(),
                descriptor.source(),
                framework.status(),
                descriptor.description(),
                serializer.serialize(framework.modelRoot()),
                framework.registeredAt());
    }

    private CompetencyFramework toDomain(FrameworkModelJpaEntity entity) {
        return CompetencyFramework.reconstitute(
                new CompetencyFrameworkId(entity.getId()),
                new FrameworkDescriptor(
                        new FrameworkName(entity.getName()),
                        new FrameworkVersion(entity.getVersion()),
                        entity.getSource(),
                        entity.getDescription()),
                entity.getStatus(),
                serializer.deserialize(entity.getContent()),
                entity.getRegisteredAt());
    }
}
