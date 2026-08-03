package ie.ul.egas.competency.infrastructure.persistence;

import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.ModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data internals of the persistence adapter — invisible outside this package. The
 * interface projection makes listings a metadata-columns-only query: the jsonb content is
 * never fetched, never deserialised on the list path (performance gate for this step).
 */
interface FrameworkModelSpringDataRepository extends JpaRepository<FrameworkModelJpaEntity, UUID> {

    boolean existsByNameAndVersion(String name, String version);

    List<FrameworkSummaryView> findAllByOrderByRegisteredAtDesc();

    interface FrameworkSummaryView {
        UUID getId();
        String getName();
        String getVersion();
        FrameworkSource getSource();
        ModelStatus getStatus();
        Instant getRegisteredAt();
    }
}
