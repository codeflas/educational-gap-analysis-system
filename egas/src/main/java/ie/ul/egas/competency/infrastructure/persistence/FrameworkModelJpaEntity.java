package ie.ul.egas.competency.infrastructure.persistence;

import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.ModelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence mapping record for a framework model — an adapter-private artifact, never a
 * domain object (the domain aggregate is {@code CompetencyFramework}). Metadata columns are the
 * queryable projection; {@code content} holds the emfjson-serialised M1 graph in jsonb
 * (ADR-005: queryable-in-place later via jsonb operators/GIN, unlike an opaque XMI blob).
 */
@Entity
@Table(name = "framework_model", schema = "competency",
        uniqueConstraints = @UniqueConstraint(name = "uq_framework_name_version",
                columnNames = {"name", "version"}))
class FrameworkModelJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FrameworkSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModelStatus status;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected FrameworkModelJpaEntity() {
        // JPA
    }

    FrameworkModelJpaEntity(UUID id, String name, String version, FrameworkSource source,
                            ModelStatus status, String description, String content, Instant registeredAt) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.source = source;
        this.status = status;
        this.description = description;
        this.content = content;
        this.registeredAt = registeredAt;
    }

    UUID getId() { return id; }
    String getName() { return name; }
    String getVersion() { return version; }
    FrameworkSource getSource() { return source; }
    ModelStatus getStatus() { return status; }
    String getDescription() { return description; }
    String getContent() { return content; }
    Instant getRegisteredAt() { return registeredAt; }
}
