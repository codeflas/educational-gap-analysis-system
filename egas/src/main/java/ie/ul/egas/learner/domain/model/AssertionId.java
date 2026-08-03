package ie.ul.egas.learner.domain.model;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link ProficiencyAssertion} within a learner profile.
 *
 * <p>Module-internal on purpose: it lives in {@code domain.model}, not in the published
 * {@code api} package, because no other context has any business addressing an individual
 * assertion. Gap Analysis reasons about a learner's proficiency, not about the bookkeeping entity
 * that records it.
 */
public record AssertionId(UUID value) implements Identifier {

    public AssertionId {
        Objects.requireNonNull(value, "AssertionId requires a non-null UUID");
    }

    public static AssertionId random() {
        return new AssertionId(UUID.randomUUID());
    }
}
