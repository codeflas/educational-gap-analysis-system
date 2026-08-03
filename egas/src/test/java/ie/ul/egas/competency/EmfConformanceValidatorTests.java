package ie.ul.egas.competency;

import ie.ul.egas.competency.application.FrameworkModelAssembler;
import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import ie.ul.egas.competency.domain.validation.ConformanceReport;
import ie.ul.egas.competency.domain.validation.ConformanceViolation;
import ie.ul.egas.competency.domain.validation.EmfConformanceValidator;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance rules: structural (Diagnostician, M2-driven) and bespoke invariants. Invalid
 * models the assembler can produce come from fixtures; states it cannot produce (wrong root,
 * missing required attribute) are built directly against the metamodel.
 */
class EmfConformanceValidatorTests {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();
    private final FrameworkModelAssembler assembler = new FrameworkModelAssembler();
    private final EmfConformanceValidator validator = new EmfConformanceValidator();

    @Test
    void aWellFormedModelConforms() {
        ConformanceReport report = validator.validate(assembler.assemble(FrameworkFixtures.validCommand()));

        assertThat(report.conforms()).as(report.violations().toString()).isTrue();
        assertThat(report.violations()).isEmpty();
    }

    @Test
    void rejectsWrongRootType() {
        EObject area = mm.create(mm.competencyArea());

        ConformanceReport report = validator.validate(area);

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations()).extracting(ConformanceViolation::code)
                .containsExactly("WRONG_ROOT_TYPE");
    }

    @Test
    void diagnosticianFlagsMissingRequiredFeatures() {
        // Framework with no areas and unset mandatory attributes: structural violations from M2.
        EObject root = mm.create(mm.framework());

        ConformanceReport report = validator.validate(root);

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations()).extracting(ConformanceViolation::code).contains("STRUCTURAL");
    }

    @Test
    void flagsBlankMandatoryText() {
        EObject root = assembler.assemble(FrameworkFixtures.validCommand());
        root.eSet(mm.frameworkName(), "   ");

        ConformanceReport report = validator.validate(root);

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations()).extracting(ConformanceViolation::code).contains("BLANK_ATTRIBUTE");
    }

    @Test
    void flagsDuplicateCompetencyCodes() {
        ConformanceReport report = validator.validate(
                assembler.assemble(FrameworkFixtures.commandWithDuplicateCompetencyCodes()));

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations()).extracting(ConformanceViolation::code)
                .contains("DUPLICATE_COMPETENCY_CODE");
    }

    @Test
    void flagsSelfPrerequisite() {
        EObject root = assembler.assemble(FrameworkFixtures.validCommand());
        EObject design = firstCompetency(root);
        many(design, mm.competencyPrerequisites()).add(design);

        ConformanceReport report = validator.validate(root);

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations()).extracting(ConformanceViolation::code).contains("SELF_PREREQUISITE");
    }

    @Test
    void detectsPrerequisiteCyclesWithPath() {
        ConformanceReport report = validator.validate(
                assembler.assemble(FrameworkFixtures.commandWithPrerequisiteCycle()));

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations())
                .filteredOn(v -> v.code().equals("CYCLIC_PREREQUISITES"))
                .hasSize(1)
                .first()
                .satisfies(v -> assertThat(v.message()).contains("->"));
    }

    @Test
    void flagsForeignPrerequisiteReferences() {
        EObject rootA = assembler.assemble(FrameworkFixtures.validCommand());
        EObject rootB = assembler.assemble(FrameworkFixtures.validCommand("Other Framework", "2.0"));
        many(firstCompetency(rootA), mm.competencyPrerequisites()).add(firstCompetency(rootB));

        ConformanceReport report = validator.validate(rootA);

        assertThat(report.conforms()).isFalse();
        assertThat(report.violations()).extracting(ConformanceViolation::code).contains("FOREIGN_REFERENCE");
    }

    private EObject firstCompetency(EObject root) {
        EObject firstArea = many(root, mm.frameworkAreas()).get(0);
        return many(firstArea, mm.areaCompetencies()).get(0);
    }

    @SuppressWarnings("unchecked")
    private EList<EObject> many(EObject owner, org.eclipse.emf.ecore.EReference reference) {
        return (EList<EObject>) owner.eGet(reference);
    }
}
