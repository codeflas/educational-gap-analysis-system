package ie.ul.egas.competency;

import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidence generator, same pattern as the Modulith Documenter: every build emits the citable
 * {@code .ecore} artifact of the metamodel (M2) to {@code target/dissertation/}. When the
 * metamodel freezes (end of W3, ADR-003) this artifact is the input to the one-off genmodel run.
 */
class MetamodelEvidenceTests {

    @Test
    void emitsTheEcoreArtefactForTheDissertation() throws IOException {
        Path directory = Path.of("target", "dissertation");
        Files.createDirectories(directory);
        Path file = directory.resolve("competency-metamodel-v1.ecore").toAbsolutePath();

        ResourceSetImpl resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new XMIResourceFactoryImpl());
        Resource resource = resourceSet.createResource(URI.createFileURI(file.toString()));
        resource.getContents().add(EcoreUtil.copy(CompetencyMetamodel.instance().ePackage()));
        resource.save(Map.of());

        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.size(file)).isPositive();
    }
}
