package ie.ul.egas.competency.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Framework version label. Deliberately NOT restricted to semantic versioning: real competency
 * frameworks version as "8" (SFIA), "v1.1.0" (ESCO) or "2023" — over-constraining here would
 * undermine the framework-independence claim (RQ1). A permissive character-class sanity check
 * plus a length bound is the right amount of validation.
 */
public record FrameworkVersion(String value) {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,49}");

    public FrameworkVersion {
        Objects.requireNonNull(value, "Framework version must not be null");
        value = value.trim();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Framework version must match " + PATTERN.pattern() + " but was '" + value + "'");
        }
    }
}
