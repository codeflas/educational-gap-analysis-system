/**
 * Shared kernel — deliberately minimal.
 *
 * <p>Only stable, domain-neutral primitives may live here; anything context-specific belongs to
 * the owning module's {@code api} package. An inflated shared kernel is the classic route to
 * hidden coupling (Evans 2004) and would corrupt the coupling evidence gathered for RQ2, so
 * additions require explicit justification in the decision log.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared Kernel",
        allowedDependencies = {})
package ie.ul.egas.shared;
