package ie.ul.egas.competency;

import ie.ul.egas.competency.application.FrameworkModelAssembler;
import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import ie.ul.egas.competency.domain.validation.ConformanceViolation;
import ie.ul.egas.competency.domain.validation.ModelConformanceException;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T2M injection: command → conforming EObject graph, with two-pass reference resolution. */
class FrameworkModelAssemblerTests {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();
    private final FrameworkModelAssembler assembler = new FrameworkModelAssembler();

    @Test
    void buildsAFullyPopulatedModelGraph() {
        EObject root = assembler.assemble(FrameworkFixtures.validCommand());

        assertThat(root.eClass()).isSameAs(mm.framework());
        assertThat(root.eGet(mm.frameworkName())).isEqualTo("Software Engineering Core");
        assertThat(many(root, mm.frameworkLevels())).hasSize(3);
        assertThat(many(root, mm.frameworkAreas())).hasSize(2);

        EObject design = many(root, mm.frameworkAreas()).get(0);
        EObject architecture = many(design, mm.areaCompetencies()).get(1);
        assertThat(architecture.eGet(mm.competencyCode())).isEqualTo("SE-ARC");

        List<EObject> prerequisites = many(architecture, mm.competencyPrerequisites());
        assertThat(prerequisites).hasSize(1);
        assertThat(prerequisites.get(0).eGet(mm.competencyCode())).isEqualTo("SE-DSN");
    }

    @Test
    void resolvesLevelDescriptorReferencesAgainstFrameworkLevels() {
        EObject root = assembler.assemble(FrameworkFixtures.validCommand());
        EObject design = many(root, mm.frameworkAreas()).get(0);
        EObject softwareDesign = many(design, mm.areaCompetencies()).get(0);
        EObject descriptor = many(softwareDesign, mm.competencyLevelDescriptors()).get(0);

        EObject level = (EObject) descriptor.eGet(mm.levelDescriptorLevel());
        assertThat(level.eGet(mm.levelCode())).isEqualTo("L2");
    }

    @Test
    void rejectsUnknownPrerequisiteCodesAsConformanceViolations() {
        assertThatThrownBy(() -> assembler.assemble(FrameworkFixtures.commandWithUnknownPrerequisite()))
                .isInstanceOfSatisfying(ModelConformanceException.class, e ->
                        assertThat(e.report().violations())
                                .extracting(ConformanceViolation::code)
                                .containsExactly("UNRESOLVED_PREREQUISITE"));
    }

    @Test
    void rejectsUnknownLevelCodesAsConformanceViolations() {
        assertThatThrownBy(() -> assembler.assemble(FrameworkFixtures.commandWithUnknownLevel()))
                .isInstanceOfSatisfying(ModelConformanceException.class, e ->
                        assertThat(e.report().violations())
                                .extracting(ConformanceViolation::code)
                                .containsExactly("UNKNOWN_LEVEL"));
    }

    @SuppressWarnings("unchecked")
    private List<EObject> many(EObject owner, org.eclipse.emf.ecore.EReference reference) {
        return (List<EObject>) owner.eGet(reference);
    }
}
