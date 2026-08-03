package ie.ul.egas.competency.api;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a registered competency framework model (an M1 model instance, e.g. an imported
 * SFIA or ESCO subset, or a bespoke curriculum framework).
 */
public record CompetencyFrameworkId(UUID value) implements Identifier {

    public CompetencyFrameworkId {
        Objects.requireNonNull(value, "CompetencyFrameworkId requires a non-null UUID");
    }

    public static CompetencyFrameworkId random() {
        return new CompetencyFrameworkId(UUID.randomUUID());
    }

    public static CompetencyFrameworkId of(String raw) {
        return new CompetencyFrameworkId(UUID.fromString(raw));
    }
}
