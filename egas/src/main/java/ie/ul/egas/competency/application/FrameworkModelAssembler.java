package ie.ul.egas.competency.application;

import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import ie.ul.egas.competency.domain.validation.ConformanceReport;
import ie.ul.egas.competency.domain.validation.ConformanceViolation;
import ie.ul.egas.competency.domain.validation.ModelConformanceException;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text-to-model (T2M) injection: transforms the registration command into an M1 EObject graph
 * conforming to the competency metamodel. In MDE terms this is the system's first first-class
 * transformation (ADR-002); the W10 external-framework importer will be its M2M sibling.
 *
 * <p>Reference resolution is two-pass — competencies are created first, prerequisite
 * cross-references wired second — because a prerequisite may name a competency defined later in
 * the payload. Unresolvable references (unknown prerequisite or level codes) are collected and
 * rejected as a {@link ModelConformanceException} <em>before</em> the aggregate is asked to
 * register: an unresolvable reference cannot be represented in the model at all, so it is
 * injection-time conformance, distinct from the model-level validation the aggregate runs.
 *
 * <p>Duplicate codes are resolved first-occurrence-wins here; the conformance validator flags
 * the duplication itself as an error, so such a model can never be registered anyway.
 */
@Component
public class FrameworkModelAssembler {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();

    public EObject assemble(RegisterFrameworkCommand command) {
        List<ConformanceViolation> unresolved = new ArrayList<>();

        EObject root = mm.create(mm.framework());
        root.eSet(mm.frameworkName(), command.name());
        root.eSet(mm.frameworkVersion(), command.version());
        if (hasText(command.description())) {
            root.eSet(mm.frameworkDescription(), command.description());
        }
        root.eSet(mm.frameworkSource(), mm.sourceLiteral(command.source().name()));

        Map<String, EObject> levelsByCode = new LinkedHashMap<>();
        for (RegisterFrameworkCommand.Level level : command.levels()) {
            EObject levelObject = mm.create(mm.proficiencyLevel());
            levelObject.eSet(mm.levelCode(), level.code());
            if (hasText(level.name())) {
                levelObject.eSet(mm.levelName(), level.name());
            }
            levelObject.eSet(mm.levelOrdinal(), level.ordinal());
            many(root, mm.frameworkLevels()).add(levelObject);
            levelsByCode.putIfAbsent(level.code(), levelObject);
        }

        Map<String, EObject> competenciesByCode = new HashMap<>();
        List<PendingPrerequisites> pending = new ArrayList<>();

        for (RegisterFrameworkCommand.Area area : command.areas()) {
            EObject areaObject = mm.create(mm.competencyArea());
            areaObject.eSet(mm.areaCode(), area.code());
            areaObject.eSet(mm.areaName(), area.name());
            if (hasText(area.description())) {
                areaObject.eSet(mm.areaDescription(), area.description());
            }
            many(root, mm.frameworkAreas()).add(areaObject);

            for (RegisterFrameworkCommand.Competency competency : area.competencies()) {
                EObject competencyObject = mm.create(mm.competency());
                competencyObject.eSet(mm.competencyCode(), competency.code());
                competencyObject.eSet(mm.competencyName(), competency.name());
                if (hasText(competency.description())) {
                    competencyObject.eSet(mm.competencyDescription(), competency.description());
                }
                many(areaObject, mm.areaCompetencies()).add(competencyObject);
                competenciesByCode.putIfAbsent(competency.code(), competencyObject);

                for (RegisterFrameworkCommand.LevelDescriptor descriptor : competency.levelDescriptors()) {
                    EObject level = levelsByCode.get(descriptor.levelCode());
                    if (level == null) {
                        unresolved.add(ConformanceViolation.error("UNKNOWN_LEVEL",
                                "Level descriptor references undefined proficiency level '"
                                        + descriptor.levelCode() + "'",
                                "Competency '" + competency.code() + "'"));
                        continue;
                    }
                    EObject descriptorObject = mm.create(mm.levelDescriptor());
                    descriptorObject.eSet(mm.levelDescriptorLevel(), level);
                    if (hasText(descriptor.descriptor())) {
                        descriptorObject.eSet(mm.levelDescriptorText(), descriptor.descriptor());
                    }
                    many(competencyObject, mm.competencyLevelDescriptors()).add(descriptorObject);
                }

                pending.add(new PendingPrerequisites(
                        competencyObject, competency.code(), competency.prerequisites()));
            }
        }

        for (PendingPrerequisites entry : pending) {
            for (String prerequisiteCode : entry.prerequisiteCodes()) {
                EObject target = competenciesByCode.get(prerequisiteCode);
                if (target == null) {
                    unresolved.add(ConformanceViolation.error("UNRESOLVED_PREREQUISITE",
                            "Prerequisite references unknown competency '" + prerequisiteCode + "'",
                            "Competency '" + entry.code() + "'"));
                } else {
                    many(entry.competency(), mm.competencyPrerequisites()).add(target);
                }
            }
        }

        if (!unresolved.isEmpty()) {
            throw new ModelConformanceException(new ConformanceReport(unresolved));
        }
        return root;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private EList<EObject> many(EObject owner, EReference reference) {
        return (EList<EObject>) owner.eGet(reference);
    }

    private record PendingPrerequisites(EObject competency, String code, List<String> prerequisiteCodes) {
    }
}
