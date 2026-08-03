package ie.ul.egas.competency.infrastructure.persistence;

import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.application.FrameworkModelAssembler;
import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip fidelity of the emfjson bridge: structural model equality after
 * serialise → deserialise, including resolution of intra-resource cross-references.
 * Co-located with the adapter package to exercise its package-private surface directly.
 */
class EmfJsonModelSerializerTests {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();
    private final FrameworkModelAssembler assembler = new FrameworkModelAssembler();
    private final EmfJsonModelSerializer serializer = new EmfJsonModelSerializer();

    @Test
    void roundTripPreservesTheModelStructurally() {
        EObject original = assembler.assemble(FrameworkFixtures.validCommand());

        String json = serializer.serialize(original);
        EObject restored = serializer.deserialize(json);

        assertThat(json).contains(CompetencyMetamodel.NS_URI);
        assertThat(EcoreUtil.equals(original, restored))
                .as("restored model must be structurally equal to the original")
                .isTrue();
    }

    @Test
    void roundTripPreservesPrerequisiteCrossReferences() {
        EObject original = assembler.assemble(FrameworkFixtures.validCommand());

        EObject restored = serializer.deserialize(serializer.serialize(original));

        EObject firstArea = many(restored, mm.frameworkAreas()).get(0);
        EObject architecture = many(firstArea, mm.areaCompetencies()).get(1);
        List<EObject> prerequisites = many(architecture, mm.competencyPrerequisites());

        assertThat(prerequisites).hasSize(1);
        assertThat(prerequisites.get(0).eGet(mm.competencyCode())).isEqualTo("SE-DSN");
        assertThat(EcoreUtil.getRootContainer(prerequisites.get(0)))
                .as("resolved prerequisite must live inside the restored model")
                .isSameAs(restored);
    }

    @Test
    void serialisationDoesNotMutateTheAggregateGraph() {
        EObject original = assembler.assemble(FrameworkFixtures.validCommand());

        serializer.serialize(original);

        assertThat(original.eResource())
                .as("copy-before-attach: the live graph must never be claimed by a Resource")
                .isNull();
    }

    @SuppressWarnings("unchecked")
    private List<EObject> many(EObject owner, org.eclipse.emf.ecore.EReference reference) {
        return (List<EObject>) owner.eGet(reference);
    }
}
