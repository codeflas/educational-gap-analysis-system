package ie.ul.egas.competency.domain.model;

import java.util.Objects;

/**
 * Human-readable framework name. Value object: validating, immutable, equality by value.
 * Trimmed on construction so that equality and the (name, version) uniqueness rule are
 * insensitive to accidental surrounding whitespace.
 */
public record FrameworkName(String value) {

    private static final int MAX_LENGTH = 200;

    public FrameworkName {
        Objects.requireNonNull(value, "Framework name must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Framework name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Framework name must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
