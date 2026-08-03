package ie.ul.egas.competency.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read model for framework listings. Deliberately excludes the model content so that list
 * queries are satisfiable from metadata columns alone — no jsonb fetch, no EMF
 * deserialisation. This is lazy column selection inside the module, NOT the ADR-007 CQRS
 * projection (which is a cross-module, event-rebuilt structure owned by Gap Analysis).
 */
public record FrameworkSummary(
        CompetencyFrameworkId id,
        FrameworkName name,
        FrameworkVersion version,
        FrameworkSource source,
        ModelStatus status,
        Instant registeredAt) {

    public FrameworkSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
    }
}
