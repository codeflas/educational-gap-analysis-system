package ie.ul.egas.competency.domain.validation;

import org.eclipse.emf.ecore.EObject;

/**
 * Domain service contract for M1-against-M2 conformance validation. An interface for two
 * reasons: Dependency Inversion (the aggregate depends on the abstraction, wired at the
 * application boundary) and a clean test seam (aggregate tests stub it with a lambda, so the
 * invariant "only conforming models can be registered" is testable without EMF fixtures).
 */
@FunctionalInterface
public interface ConformanceValidator {

    ConformanceReport validate(EObject modelRoot);
}
