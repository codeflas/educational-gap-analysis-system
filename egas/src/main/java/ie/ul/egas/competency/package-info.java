/**
 * Competency Modelling context — owns the Ecore metamodel (M2), the lifecycle of competency
 * framework models (M1) and their conformance validation (ADR-002/ADR-003).
 *
 * <p>Internal structure follows ports-and-adapters:
 * {@code api} (published contracts, named interface "api"), {@code domain} (framework-free model
 * concepts and driven ports), {@code application} (use-case services, transaction boundary),
 * {@code infrastructure} (web/persistence/EMF adapters). Only {@code api} is visible to other
 * modules.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Competency Modelling",
        allowedDependencies = {})
package ie.ul.egas.competency;
