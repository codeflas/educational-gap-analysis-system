package ie.ul.egas.competency.domain.validation;

import java.util.Objects;

/**
 * Raised when a model fails conformance validation; carries the full report so the web adapter
 * can render an RFC 9457 problem detail with a machine-readable violation list.
 */
public class ModelConformanceException extends RuntimeException {

    private final transient ConformanceReport report;

    public ModelConformanceException(ConformanceReport report) {
        super("Model does not conform to the competency metamodel: %d violation(s)"
                .formatted(Objects.requireNonNull(report, "report must not be null").violations().size()));
        this.report = report;
    }

    public ConformanceReport report() { return report; }
}
