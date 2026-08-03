package ie.ul.egas.learner.domain.model;

import java.util.Objects;

/**
 * The authenticated principal a learner profile belongs to — the JWT {@code sub} claim, carried
 * into the domain as an opaque value (ADR-016, ADR-017).
 *
 * <p>Deliberately <em>not</em> parsed, split, or validated as a username. Nothing in the domain
 * interprets its content, so when ADR-013's anticipated substitution arrives — a real identity
 * source supplying stable user identifiers in place of configuration-backed development
 * principals — the string's content changes and no signature, column, or rule changes with it.
 *
 * <p>Trimmed on construction so that the one-profile-per-subject invariant, and the ownership
 * comparison that depends on it, are insensitive to incidental whitespace.
 */
public record AuthSubject(String value) {

    private static final int MAX_LENGTH = 200;

    public AuthSubject {
        Objects.requireNonNull(value, "Authenticated subject must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Authenticated subject must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Authenticated subject must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
