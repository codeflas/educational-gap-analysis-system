package ie.ul.egas.competency.domain.validation;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of validating an M1 model against the metamodel and its invariants. A model conforms
 * when it has no ERROR-severity violations; warnings are informational and never block
 * registration.
 */
public record ConformanceReport(List<ConformanceViolation> violations) {

    public ConformanceReport {
        Objects.requireNonNull(violations, "violations must not be null");
        violations = List.copyOf(violations);
    }

    public boolean conforms() {
        return violations.stream().noneMatch(v -> v.severity() == ConformanceViolation.Severity.ERROR);
    }
}
