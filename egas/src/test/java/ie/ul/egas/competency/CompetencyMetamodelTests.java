package ie.ul.egas.competency;

import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 sanity: the dynamic metamodel exposes exactly the intended structure. Guards against
 * silent drift while the metamodel remains programmatic (pre-freeze, ADR-003).
 */
class CompetencyMetamodelTests {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();

    @Test
    void definesTheFiveClassesAndSourceEnum() {
        assertThat(mm.ePackage().getNsURI()).isEqualTo(CompetencyMetamodel.NS_URI);
        assertThat(mm.framework().getName()).isEqualTo("CompetencyFramework");
        assertThat(mm.proficiencyLevel().getName()).isEqualTo("ProficiencyLevel");
        assertThat(mm.competencyArea().getName()).isEqualTo("CompetencyArea");
        assertThat(mm.competency().getName()).isEqualTo("Competency");
        assertThat(mm.levelDescriptor().getName()).isEqualTo("LevelDescriptor");
        assertThat(mm.frameworkSourceKind().getELiterals())
                .extracting("name")
                .containsExactly("BESPOKE", "SFIA", "ESCO", "OTHER");
    }

    @Test
    void containmentAndCrossReferenceShapesMatchTheDesign() {
        assertThat(mm.frameworkAreas().isContainment()).isTrue();
        assertThat(mm.frameworkAreas().getLowerBound()).isEqualTo(1);
        assertThat(mm.frameworkLevels().isContainment()).isTrue();
        assertThat(mm.frameworkLevels().getLowerBound()).isZero();
        assertThat(mm.areaCompetencies().isContainment()).isTrue();
        assertThat(mm.competencyPrerequisites().isContainment())
                .as("prerequisites are cross-references, not containment")
                .isFalse();
        assertThat(mm.levelDescriptorLevel().isContainment()).isFalse();
        assertThat(mm.levelDescriptorLevel().getLowerBound()).isEqualTo(1);
    }

    @Test
    void mandatoryAttributesAreRequiredInTheMetamodel() {
        assertThat(mm.frameworkName().getLowerBound()).isEqualTo(1);
        assertThat(mm.frameworkVersion().getLowerBound()).isEqualTo(1);
        assertThat(mm.competencyCode().getLowerBound()).isEqualTo(1);
        assertThat(mm.frameworkDescription().getLowerBound()).isZero();
    }

    @Test
    void rejectsUnknownSourceLiterals() {
        assertThatThrownBy(() -> mm.sourceLiteral("NOPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
