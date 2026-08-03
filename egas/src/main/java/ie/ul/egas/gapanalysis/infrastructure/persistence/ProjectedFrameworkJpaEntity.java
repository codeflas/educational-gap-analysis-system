package ie.ul.egas.gapanalysis.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence mapping for a projected framework — an adapter-private artifact, never a domain
 * object (the domain read model is {@code ProjectedCompetencyModel}).
 *
 * <p>Levels are an {@code @ElementCollection} rather than an entity association: a level has no
 * identity of its own outside the framework that defines it, and modelling it as an entity would
 * invent one. A {@code Set} rather than a {@code List} for the reason Step 4 established — Hibernate
 * refuses to fetch two bags together, and this projection is read with its collections joined.
 */
@Entity
@Table(name = "projected_framework", schema = "gap_analysis")
class ProjectedFrameworkJpaEntity {

    @Id
    @Column(name = "framework_id")
    private UUID frameworkId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    @ElementCollection
    @CollectionTable(name = "projected_level", schema = "gap_analysis",
            joinColumns = @JoinColumn(name = "framework_id"))
    @OrderBy("ordinal asc, code asc")
    private Set<ProjectedLevelEmbeddable> levels = new LinkedHashSet<>();

    protected ProjectedFrameworkJpaEntity() {
        // JPA
    }

    ProjectedFrameworkJpaEntity(UUID frameworkId, String name, String version,
                                Instant registeredAt, Instant projectedAt) {
        this.frameworkId = frameworkId;
        this.name = name;
        this.version = version;
        this.registeredAt = registeredAt;
        this.projectedAt = projectedAt;
    }

    void addLevel(ProjectedLevelEmbeddable level) {
        levels.add(level);
    }

    UUID getFrameworkId() { return frameworkId; }
    String getName() { return name; }
    String getVersion() { return version; }
    Instant getRegisteredAt() { return registeredAt; }
    Instant getProjectedAt() { return projectedAt; }
    Set<ProjectedLevelEmbeddable> getLevels() { return levels; }
}
