package ie.ul.egas.competency.infrastructure.web.dto;

import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.ModelStatus;

import java.time.Instant;
import java.util.UUID;

/** Listing/creation response: metadata only, no model content. */
public record FrameworkSummaryResponse(
        UUID id,
        String name,
        String version,
        FrameworkSource source,
        ModelStatus status,
        Instant registeredAt) {
}
