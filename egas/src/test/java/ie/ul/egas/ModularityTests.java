package ie.ul.egas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architectural fitness functions (Ford, Parsons &amp; Kua 2017) for the module topology.
 *
 * <p>{@link ApplicationModules#verify()} fails the build on: (a) access to another module's
 * internal (non-{@code api}) packages, (b) dependencies not declared in a module's
 * {@code allowedDependencies}, and (c) cyclic dependencies between modules. Together with
 * {@link HexagonalArchitectureTests} this makes the dissertation's modularity claims
 * continuously machine-checked rather than asserted.
 *
 * <p>The {@link Documenter} output (PlantUML component diagrams plus per-module canvases under
 * {@code target/spring-modulith-docs}) is uploaded by CI on every build and feeds the
 * architecture chapter directly — generated evidence, not hand-drawn diagrams that drift.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(EgasApplication.class);

    @Test
    void moduleTopologyIsValid() {
        modules.verify();
    }

    @Test
    void generateArchitectureDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
