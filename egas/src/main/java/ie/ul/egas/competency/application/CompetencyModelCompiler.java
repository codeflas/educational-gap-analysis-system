package ie.ul.egas.competency.application;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.competency.api.CompetencyModelSnapshot;
import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens an interpreted M1 model into the published {@link CompetencyModelSnapshot} (ADR-007).
 *
 * <p><b>This is the boundary where EMF stops.</b> The traversal happens here, inside Competency
 * Modelling, precisely so that no {@code EObject} reaches a consumer — ADR-012 confines EMF to this
 * module and the {@code emfConfinedToCompetencyModule} fitness function enforces it. What leaves is
 * records.
 *
 * <p>Application rather than domain, and package-private: this is model-to-model transformation
 * driven by an integration need, not a rule the domain enforces about itself. It reads the model
 * through the {@link CompetencyMetamodel} façade for the same reason {@code FrameworkModelAssembler}
 * writes through it — dynamic EMF is stringly typed, and the façade is where that is contained.
 */
@Component
class CompetencyModelCompiler {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();

    CompetencyModelSnapshot compile(CompetencyFramework framework) {
        EObject root = framework.modelRoot();

        List<CompetencyModelSnapshot.Level> levels = many(root, mm.frameworkLevels()).stream()
                .map(level -> new CompetencyModelSnapshot.Level(
                        str(level, mm.levelCode()),
                        str(level, mm.levelName()),
                        (Integer) level.eGet(mm.levelOrdinal())))
                .toList();

        List<CompetencyModelSnapshot.Competency> competencies = new ArrayList<>();
        for (EObject area : many(root, mm.frameworkAreas())) {
            String areaCode = str(area, mm.areaCode());
            for (EObject competency : many(area, mm.areaCompetencies())) {
                competencies.add(toCompetency(competency, areaCode, framework.id()));
            }
        }

        return new CompetencyModelSnapshot(
                framework.descriptor().name().value(),
                framework.descriptor().version().value(),
                levels,
                competencies);
    }

    private CompetencyModelSnapshot.Competency toCompetency(EObject competency, String areaCode,
                                                            CompetencyFrameworkId frameworkId) {
        String code = str(competency, mm.competencyCode());
        return new CompetencyModelSnapshot.Competency(
                CompetencyId.forCompetency(frameworkId, code),
                code,
                str(competency, mm.competencyName()),
                areaCode,
                definedLevelCodes(competency));
    }

    /**
     * The levels this competency has a descriptor for — what the model says it means at each level.
     * Not a requirement: nothing in the metamodel states a level anyone must reach (ADR-021).
     */
    private List<String> definedLevelCodes(EObject competency) {
        return many(competency, mm.competencyLevelDescriptors()).stream()
                .map(descriptor -> (EObject) descriptor.eGet(mm.levelDescriptorLevel()))
                .filter(level -> level != null)
                .map(level -> str(level, mm.levelCode()))
                .toList();
    }

    private String str(EObject object, EAttribute attribute) {
        Object value = object.eGet(attribute);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<EObject> many(EObject owner, EReference reference) {
        return (List<EObject>) owner.eGet(reference);
    }
}
