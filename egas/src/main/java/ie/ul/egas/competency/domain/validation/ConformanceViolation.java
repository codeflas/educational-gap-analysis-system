package ie.ul.egas.competency.domain.validation;

import java.util.Objects;

/**
 * A single conformance finding: severity, a stable machine-readable code (asserted by tests and
 * consumed by API clients), a human-readable message, and the model location it concerns.
 */
public record ConformanceViolation(Severity severity, String code, String message, String location) {

    public enum Severity { ERROR, WARNING }

    public ConformanceViolation {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(location, "location must not be null");
    }

    public static ConformanceViolation error(String code, String message, String location) {
        return new ConformanceViolation(Severity.ERROR, code, message, location);
    }
}
