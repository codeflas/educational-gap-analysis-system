package ie.ul.egas.recommendation.api;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Identity of a synthesised learning pathway recommendation. */
public record LearningPathwayId(UUID value) implements Identifier {

    public LearningPathwayId {
        Objects.requireNonNull(value, "LearningPathwayId requires a non-null UUID");
    }

    public static LearningPathwayId random() {
        return new LearningPathwayId(UUID.randomUUID());
    }

    public static LearningPathwayId of(String raw) {
        return new LearningPathwayId(UUID.fromString(raw));
    }
}
