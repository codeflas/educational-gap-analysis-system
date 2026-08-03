package ie.ul.egas.learner.api;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a learner profile. Distinct from any authentication principal id on purpose:
 * the security identity (platform concern) and the domain identity (profiling concern) must be
 * free to evolve independently; the mapping between them is an application-layer concern.
 */
public record LearnerId(UUID value) implements Identifier {

    public LearnerId {
        Objects.requireNonNull(value, "LearnerId requires a non-null UUID");
    }

    public static LearnerId random() {
        return new LearnerId(UUID.randomUUID());
    }

    public static LearnerId of(String raw) {
        return new LearnerId(UUID.fromString(raw));
    }
}
