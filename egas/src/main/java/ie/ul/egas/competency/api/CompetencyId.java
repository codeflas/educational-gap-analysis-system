package ie.ul.egas.competency.api;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a single competency element within a framework model. Downstream contexts
 * (Learner Profiling, Gap Analysis, Catalogue, Recommendation) refer to competencies solely
 * through this value object — never through model objects — preserving context autonomy.
 */
public record CompetencyId(UUID value) implements Identifier {

    public CompetencyId {
        Objects.requireNonNull(value, "CompetencyId requires a non-null UUID");
    }

    public static CompetencyId random() {
        return new CompetencyId(UUID.randomUUID());
    }

    public static CompetencyId of(String raw) {
        return new CompetencyId(UUID.fromString(raw));
    }
}
