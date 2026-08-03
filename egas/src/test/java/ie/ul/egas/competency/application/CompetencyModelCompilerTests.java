package ie.ul.egas.competency.application;

import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.competency.api.CompetencyModelSnapshot;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.FrameworkDescriptor;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.FrameworkVersion;
import ie.ul.egas.competency.domain.validation.EmfConformanceValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compilation of an interpreted M1 model into the published snapshot (ADR-007). Co-located with
 * the compiler to exercise its package-private surface directly.
 *
 * <p>The suite's structural point is what the snapshot must <em>not</em> contain. ADR-012 forbids
 * an {@code EObject} from crossing the module boundary, so the compiler is where the graph stops
 * and records begin — asserted here on the shape of what actually travels, which is a different
 * question from the one {@code emfConfinedToCompetencyModule} answers about package dependencies.
 */
class CompetencyModelCompilerTests {

    private final FrameworkModelAssembler assembler = new FrameworkModelAssembler();
    private final EmfConformanceValidator validator = new EmfConformanceValidator();
    private final CompetencyModelCompiler compiler = new CompetencyModelCompiler();

    @Test
    void compilesFrameworkMetadataAndTheProficiencyScale() {
        CompetencyModelSnapshot snapshot = compile("Compiler Framework", "2.1");

        assertThat(snapshot.frameworkName()).isEqualTo("Compiler Framework");
        assertThat(snapshot.frameworkVersion()).isEqualTo("2.1");
        assertThat(snapshot.levels()).extracting(CompetencyModelSnapshot.Level::code)
                .containsExactly("L1", "L2", "L3");
        assertThat(snapshot.levels()).extracting(CompetencyModelSnapshot.Level::ordinal)
                .containsExactly(1, 2, 3);
    }

    @Test
    void flattensCompetenciesAcrossAreasAndKeepsTheirAreaCode() {
        CompetencyModelSnapshot snapshot = compile("Flattening Framework", "1.0");

        assertThat(snapshot.competencies()).extracting(CompetencyModelSnapshot.Competency::code)
                .containsExactly("SE-DSN", "SE-ARC", "SE-TST");
        assertThat(snapshot.competencies()).extracting(CompetencyModelSnapshot.Competency::areaCode)
                .as("the containing area survives flattening")
                .containsExactly("DES", "DES", "QUA");
    }

    @Test
    void derivesCompetencyIdentityFromFrameworkAndCode() {
        CompetencyFrameworkId frameworkId = CompetencyFrameworkId.random();

        CompetencyId first = CompetencyId.forCompetency(frameworkId, "SE-DSN");

        assertThat(first)
                .as("stable: re-registering or re-projecting a model must not re-key it")
                .isEqualTo(CompetencyId.forCompetency(frameworkId, "SE-DSN"));
        assertThat(CompetencyId.forCompetency(frameworkId, "SE-ARC")).isNotEqualTo(first);
        assertThat(CompetencyId.forCompetency(CompetencyFrameworkId.random(), "SE-DSN"))
                .as("two frameworks reusing a code must stay apart")
                .isNotEqualTo(first);
    }

    @Test
    void theSnapshotCarriesTheDerivedIdentityForEveryCompetency() {
        CompetencyFramework framework = framework("Identity Framework", "1.0");

        CompetencyModelSnapshot snapshot = compiler.compile(framework);

        assertThat(snapshot.competencies()).allSatisfy(competency ->
                assertThat(competency.id())
                        .isEqualTo(CompetencyId.forCompetency(framework.id(), competency.code())));
    }

    @Test
    void carriesDefinedLevelCodesRatherThanAnyRequirement() {
        // The metamodel states what a level *means* for a competency, never what is demanded of
        // anyone. The snapshot therefore reports availability; the target comes from the analysis
        // request (ADR-021). SE-ARC defines no descriptors at all, which a "required level" model
        // could not have represented without inventing one.
        CompetencyModelSnapshot snapshot = compile("Levels Framework", "1.0");

        assertThat(levelsFor(snapshot, "SE-DSN")).containsExactly("L2");
        assertThat(levelsFor(snapshot, "SE-TST")).containsExactly("L1");
        assertThat(levelsFor(snapshot, "SE-ARC"))
                .as("a competency may define no levels; that is not a gap of zero")
                .isEmpty();
    }

    @Test
    void noEmfTypeReachesThePublishedSnapshot() {
        CompetencyModelSnapshot snapshot = compile("Purity Framework", "1.0");

        assertThat(snapshot.getClass().getPackageName()).isEqualTo("ie.ul.egas.competency.api");
        assertThat(snapshot.toString())
                .as("a leaked EObject would surface as an EMF class name in the record's toString")
                .doesNotContain("org.eclipse.emf");
        assertThat(snapshot.competencies()).allSatisfy(competency ->
                assertThat(competency.getClass().getName()).doesNotContain("emf"));
    }

    @Test
    void snapshotCollectionsAreDefensivelyCopied() {
        CompetencyModelSnapshot snapshot = compile("Immutability Framework", "1.0");

        assertThat(snapshot.levels()).isUnmodifiable();
        assertThat(snapshot.competencies()).isUnmodifiable();
        assertThat(snapshot.competencies().get(0).definedLevelCodes()).isUnmodifiable();
    }

    private java.util.List<String> levelsFor(CompetencyModelSnapshot snapshot, String code) {
        return snapshot.competencies().stream()
                .filter(competency -> competency.code().equals(code))
                .findFirst().orElseThrow()
                .definedLevelCodes();
    }

    private CompetencyModelSnapshot compile(String name, String version) {
        return compiler.compile(framework(name, version));
    }

    private CompetencyFramework framework(String name, String version) {
        return CompetencyFramework.register(
                new FrameworkDescriptor(new FrameworkName(name), new FrameworkVersion(version),
                        FrameworkSource.BESPOKE, null),
                assembler.assemble(FrameworkFixtures.validCommand(name, version)),
                validator,
                Clock.systemUTC());
    }
}
