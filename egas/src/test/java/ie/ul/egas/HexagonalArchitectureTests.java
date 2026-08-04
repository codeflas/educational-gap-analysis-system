package ie.ul.egas;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Layer-purity fitness functions applied uniformly inside every application module.
 *
 * <p>Spring Modulith enforces the boundaries <em>between</em> modules; these rules enforce the
 * ports-and-adapters discipline <em>within</em> them (ADR-008): a framework-free domain core,
 * an application layer blind to adapter technology, controllers confined to web adapters, and
 * published contracts ({@code api}) that depend on nothing but the JDK, the shared kernel and
 * other modules' contracts.
 *
 * <p>ADR-012 (decided in Step 2): EMF is the domain formalism of the Competency Modelling
 * context — admissible inside {@code competency} (including its domain ring) and nowhere else;
 * serialisation machinery (XMI, emfjson) stays out of the domain even there. Encoded below as
 * {@code emfConfinedToCompetencyModule} and {@code emfSerializationStaysOutOfDomain}.
 *
 * <p>The Step-1 {@code failOnEmptyShould=false} override has been removed: every rule now has a
 * populated selection, so an empty match once again fails the build (typo protection restored).
 *
 * <p>Step 5 Phase 4 strengthens {@code domainIsFrameworkFree} with {@code org.hibernate..}. The rule
 * banned JPA's own package but not Hibernate's, so a domain class annotated {@code @BatchSize},
 * {@code @Immutable} or {@code @Type} would have passed — a hole that widened as Phase 2 and Phase 4
 * added ORM-specific mapping beside a domain ring that must stay pure. This is a strengthening of an
 * existing rule rather than a new one, as ADR-016 made to {@code applicationStaysOutOfAdapters}; the
 * count remains seven ArchUnit rules plus two Spring Modulith verifications.
 */
@AnalyzeClasses(packages = "ie.ul.egas", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..")
            .because("the domain core must be testable and reusable without any framework (Clean Architecture)");

    @ArchTest
    static final ArchRule domainDoesNotReachOutwards = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .because("dependencies must point inwards only (Dependency Inversion at the architectural scale)");

    @ArchTest
    static final ArchRule applicationStaysOutOfAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..",
                    "org.springframework.web..",
                    "org.springframework.security..",
                    "jakarta.servlet..",
                    "jakarta.persistence..")
            .because("use-case services depend on ports, never on adapter technology — and, per "
                    + "ADR-016, they receive the caller's identity as an explicit command field "
                    + "rather than reading it from SecurityContextHolder's ambient state");

    @ArchTest
    static final ArchRule restControllersOnlyInWebAdapters = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..infrastructure.web..")
            .because("HTTP is a delivery mechanism and belongs to the adapter ring");

    @ArchTest
    static final ArchRule publishedContractsArePure = classes()
            .that().resideInAPackage("..api..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..",
                    "ie.ul.egas.shared..",
                    "..api..",
                    "org.springframework.modulith..")
            .because("module contracts must not leak framework or internal types across boundaries");

    @ArchTest
    static final ArchRule emfConfinedToCompetencyModule = noClasses()
            .that().resideOutsideOfPackage("ie.ul.egas.competency..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.eclipse.emf..",
                    "org.eclipse.emfcloud..")
            .because("EMF is the domain formalism of Competency Modelling only; "
                    + "EObjects never cross the module boundary (ADR-012)");

    @ArchTest
    static final ArchRule emfSerializationStaysOutOfDomain = noClasses()
            .that().resideInAPackage("ie.ul.egas.competency.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.eclipse.emf.ecore.xmi..",
                    "org.eclipse.emfcloud..")
            .because("model serialisation (XMI, emfjson) is an infrastructure concern even "
                    + "inside the owning module (ADR-012)");
}
