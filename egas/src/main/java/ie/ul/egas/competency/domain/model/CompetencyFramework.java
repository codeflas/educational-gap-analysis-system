package ie.ul.egas.competency.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.domain.validation.ConformanceReport;
import ie.ul.egas.competency.domain.validation.ConformanceValidator;
import ie.ul.egas.competency.domain.validation.ModelConformanceException;
import org.eclipse.emf.ecore.EObject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root: a registered competency framework model.
 *
 * <p>State = typed metadata ({@link FrameworkDescriptor}), lifecycle {@link ModelStatus}, and the
 * M1 model content itself — an {@link EObject} graph conforming to the competency metamodel.
 * Holding the EObject graph as domain state is the deliberate consequence of ADR-012: within
 * this context, Ecore <em>is</em> the domain formalism.
 *
 * <p><b>Central invariant, enforced inside the boundary</b>: a {@code CompetencyFramework} that
 * does not conform to M2 cannot exist. {@link #register} runs conformance validation and refuses
 * construction on failure (always-valid aggregate). The validator is passed in — the DDD
 * double-dispatch idiom — keeping this class free of service lookup and of Spring.
 *
 * <p>{@link #reconstitute} bypasses validation: the store only ever contains models that passed
 * it. Trade-off: corrupted-at-rest data would surface downstream rather than at load; defensive
 * revalidation on every read was rejected as it doubles deserialisation cost on the hot path
 * for a failure mode the schema-per-module, migration-controlled database makes remote.
 *
 * <p><b>Known limitation (documented, accepted)</b>: {@link #modelRoot()} exposes the internal
 * mutable EObject graph — EMF offers no cheap deep-immutable view, and copying on every access
 * is disproportionate. The contract is read-only access; adapters that must attach the graph to
 * a Resource (serialisation) copy first. Read-only EMF adapters are noted as future hardening.
 */
public final class CompetencyFramework {

    private final CompetencyFrameworkId id;
    private final FrameworkDescriptor descriptor;
    private final ModelStatus status;
    private final EObject modelRoot;
    private final Instant registeredAt;

    private CompetencyFramework(CompetencyFrameworkId id, FrameworkDescriptor descriptor,
                                ModelStatus status, EObject modelRoot, Instant registeredAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.modelRoot = Objects.requireNonNull(modelRoot, "modelRoot must not be null");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt must not be null");
    }

    /**
     * Registers a new framework model. The single entry point for bringing a framework into
     * existence; throws {@link ModelConformanceException} carrying the full report if the model
     * does not conform to the metamodel.
     */
    public static CompetencyFramework register(FrameworkDescriptor descriptor, EObject modelRoot,
                                               ConformanceValidator validator, Clock clock) {
        Objects.requireNonNull(validator, "validator must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        ConformanceReport report = validator.validate(modelRoot);
        if (!report.conforms()) {
            throw new ModelConformanceException(report);
        }
        return new CompetencyFramework(
                CompetencyFrameworkId.random(), descriptor, ModelStatus.DRAFT, modelRoot, clock.instant());
    }

    /** Rehydrates a previously validated framework from the store (persistence adapter only). */
    public static CompetencyFramework reconstitute(CompetencyFrameworkId id, FrameworkDescriptor descriptor,
                                                   ModelStatus status, EObject modelRoot, Instant registeredAt) {
        return new CompetencyFramework(id, descriptor, status, modelRoot, registeredAt);
    }

    public CompetencyFrameworkId id() { return id; }
    public FrameworkDescriptor descriptor() { return descriptor; }
    public ModelStatus status() { return status; }
    public EObject modelRoot() { return modelRoot; }
    public Instant registeredAt() { return registeredAt; }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof CompetencyFramework that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
