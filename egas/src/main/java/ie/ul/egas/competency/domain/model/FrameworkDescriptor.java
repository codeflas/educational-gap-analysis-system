package ie.ul.egas.competency.domain.model;

import java.util.Objects;

/**
 * Typed metadata of a framework model. Held by the aggregate AND written into the M1 model
 * itself: the model must be self-describing (exportable as a standalone artifact), while the
 * descriptor gives the rest of the system framework-free access to metadata without touching
 * EMF. Consistency between the two is guaranteed at construction time by the application
 * service, which derives both from the same command.
 */
public record FrameworkDescriptor(
        FrameworkName name,
        FrameworkVersion version,
        FrameworkSource source,
        String description) {

    public FrameworkDescriptor {
        Objects.requireNonNull(name, "Framework name must not be null");
        Objects.requireNonNull(version, "Framework version must not be null");
        Objects.requireNonNull(source, "Framework source must not be null");
        if (description != null && description.isBlank()) {
            description = null;
        }
    }
}
