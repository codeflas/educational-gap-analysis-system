package ie.ul.egas.catalogue.api;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Identity of a learning resource (course, module, reading, exercise). */
public record LearningResourceId(UUID value) implements Identifier {

    public LearningResourceId {
        Objects.requireNonNull(value, "LearningResourceId requires a non-null UUID");
    }

    public static LearningResourceId random() {
        return new LearningResourceId(UUID.randomUUID());
    }

    public static LearningResourceId of(String raw) {
        return new LearningResourceId(UUID.fromString(raw));
    }
}
