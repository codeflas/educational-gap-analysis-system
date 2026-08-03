package ie.ul.egas.competency.infrastructure.persistence;

import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emfcloud.jackson.resource.JsonResourceFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Bridges the EMF world and the jsonb column via emfjson-jackson (ADR-005): M1 graphs are
 * stored as JSON documents whose {@code eClass} discriminators reference the metamodel nsURI,
 * and whose cross-references (prerequisites, level links) are intra-resource fragment paths.
 *
 * <p><b>Per-operation {@link ResourceSet}s</b> (ADR-003): ResourceSets are not thread-safe;
 * creating one per call trades a small allocation for the elimination of shared mutable EMF
 * state under virtual-thread concurrency. The package registry maps the nsURI to the singleton,
 * effectively immutable metamodel.
 *
 * <p><b>Copy-before-attach</b>: adding an EObject to a Resource sets its {@code eResource}
 * container — a mutation of the aggregate's live model state. Serialisation therefore operates
 * on a deep copy ({@link EcoreUtil#copy}); the aggregate's graph is never touched.
 */
@Component
class EmfJsonModelSerializer {

    private static final String VIRTUAL_URI = "egas-framework.json";

    String serialize(EObject modelRoot) {
        try {
            Resource resource = newResourceSet().createResource(URI.createURI(VIRTUAL_URI));
            resource.getContents().add(EcoreUtil.copy(modelRoot));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            resource.save(out, Map.of());
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelSerializationException("Failed to serialise framework model to JSON", e);
        }
    }

    EObject deserialize(String json) {
        try {
            Resource resource = newResourceSet().createResource(URI.createURI(VIRTUAL_URI));
            resource.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Map.of());
            if (resource.getContents().isEmpty()) {
                throw new ModelSerializationException("Persisted model content is empty", null);
            }
            return resource.getContents().get(0);
        } catch (IOException e) {
            throw new ModelSerializationException("Failed to deserialise framework model from JSON", e);
        }
    }

    private ResourceSet newResourceSet() {
        ResourceSetImpl resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("json", new JsonResourceFactory());
        resourceSet.getPackageRegistry()
                .put(CompetencyMetamodel.NS_URI, CompetencyMetamodel.instance().ePackage());
        return resourceSet;
    }
}
