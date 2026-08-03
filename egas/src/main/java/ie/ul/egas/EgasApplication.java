package ie.ul.egas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * EGAS — Educational Gap Analysis System.
 *
 * <p>Composition root only: no domain logic may live in this package. The class is also the
 * anchor for Spring Modulith's module model ({@code ApplicationModules.of(EgasApplication.class)});
 * every direct sub-package of {@code ie.ul.egas} is an application module whose boundaries and
 * declared dependencies are verified by the architecture test suite (ADR-008).
 *
 * <p>{@code sharedModules = "shared"} designates the shared kernel as implicitly usable by all
 * modules, so individual {@code allowedDependencies} declarations stay focused on genuine
 * inter-context relationships.
 */
@Modulithic(systemName = "EGAS", sharedModules = "shared")
@SpringBootApplication
public class EgasApplication {

    public static void main(String[] args) {
        SpringApplication.run(EgasApplication.class, args);
    }
}
