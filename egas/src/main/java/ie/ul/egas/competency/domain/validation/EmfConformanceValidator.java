package ie.ul.egas.competency.domain.validation;

import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static ie.ul.egas.competency.domain.validation.ConformanceViolation.error;

/**
 * Reference implementation of {@link ConformanceValidator} for the dynamic metamodel phase.
 *
 * <p>Two validation tiers, by design (ADR-002):
 * <ol>
 *   <li><b>Structural conformance</b> — EMF's {@link Diagnostician} checks the model against M2
 *       mechanically: required features set, multiplicities respected, types correct. These rules
 *       live in the metamodel, not in code — changing M2 changes them.</li>
 *   <li><b>Bespoke invariants</b> — well-formedness rules Ecore cannot express (the role OCL
 *       would play in a full Eclipse stack; adopting an OCL interpreter was rejected as
 *       disproportionate for four rules): blank mandatory text, code/ordinal uniqueness,
 *       cross-reference containment ("no foreign references"), and prerequisite acyclicity —
 *       the property Gap Analysis and Recommendation depend on.</li>
 * </ol>
 *
 * <p>Violations carry stable codes so tests and API clients can assert on semantics rather than
 * message strings. Cycle detection reports the first cycle found (three-colour DFS); reporting
 * all elementary cycles is exponential and adds nothing for a rejection decision.
 */
public final class EmfConformanceValidator implements ConformanceValidator {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();

    @Override
    public ConformanceReport validate(EObject modelRoot) {
        List<ConformanceViolation> violations = new ArrayList<>();

        if (modelRoot == null) {
            violations.add(error("NULL_MODEL", "Model root must not be null", "model root"));
            return new ConformanceReport(violations);
        }
        if (modelRoot.eClass() != mm.framework()) {
            violations.add(error("WRONG_ROOT_TYPE",
                    "Model root must be a CompetencyFramework but was " + modelRoot.eClass().getName(),
                    "model root"));
            return new ConformanceReport(violations);
        }

        collectStructuralDiagnostics(modelRoot, violations);
        checkFrameworkText(modelRoot, violations);

        List<EObject> levels = many(modelRoot, mm.frameworkLevels());
        checkLevels(levels, violations);

        List<EObject> competencies = collectAndCheckAreas(modelRoot, violations);
        checkCompetencyCodes(competencies, violations);
        checkCrossReferences(modelRoot, competencies, violations);
        checkAcyclicPrerequisites(competencies, violations);

        return new ConformanceReport(violations);
    }

    // ---------------------------------------------------------------- structural (M2-driven)

    private void collectStructuralDiagnostics(EObject root, List<ConformanceViolation> out) {
        Diagnostic diagnostic = Diagnostician.INSTANCE.validate(root);
        if (diagnostic.getSeverity() == Diagnostic.OK) {
            return;
        }
        flatten(diagnostic, out);
    }

    private void flatten(Diagnostic diagnostic, List<ConformanceViolation> out) {
        if (diagnostic.getChildren().isEmpty()) {
            if (diagnostic.getSeverity() >= Diagnostic.WARNING) {
                var severity = diagnostic.getSeverity() >= Diagnostic.ERROR
                        ? ConformanceViolation.Severity.ERROR
                        : ConformanceViolation.Severity.WARNING;
                out.add(new ConformanceViolation(severity, "STRUCTURAL",
                        diagnostic.getMessage(), locationOf(diagnostic)));
            }
            return;
        }
        diagnostic.getChildren().forEach(child -> flatten(child, out));
    }

    private String locationOf(Diagnostic diagnostic) {
        for (Object datum : diagnostic.getData()) {
            if (datum instanceof EObject eObject) {
                return label(eObject);
            }
        }
        return "model";
    }

    // ---------------------------------------------------------------- bespoke invariants

    private void checkFrameworkText(EObject root, List<ConformanceViolation> out) {
        if (isBlank(root, mm.frameworkName())) {
            out.add(error("BLANK_ATTRIBUTE", "Framework name must not be blank", "CompetencyFramework"));
        }
        if (isBlank(root, mm.frameworkVersion())) {
            out.add(error("BLANK_ATTRIBUTE", "Framework version must not be blank", "CompetencyFramework"));
        }
    }

    private void checkLevels(List<EObject> levels, List<ConformanceViolation> out) {
        Set<String> codes = new HashSet<>();
        Set<Object> ordinals = new HashSet<>();
        for (EObject level : levels) {
            String code = str(level, mm.levelCode());
            if (code == null || code.isBlank()) {
                out.add(error("BLANK_ATTRIBUTE", "Proficiency level code must not be blank", label(level)));
            } else if (!codes.add(code)) {
                out.add(error("DUPLICATE_LEVEL_CODE",
                        "Proficiency level code '" + code + "' is defined more than once", label(level)));
            }
            Object ordinal = level.eGet(mm.levelOrdinal());
            if (ordinal != null && !ordinals.add(ordinal)) {
                out.add(error("DUPLICATE_LEVEL_ORDINAL",
                        "Proficiency level ordinal '" + ordinal + "' is defined more than once", label(level)));
            }
        }
    }

    private List<EObject> collectAndCheckAreas(EObject root, List<ConformanceViolation> out) {
        List<EObject> competencies = new ArrayList<>();
        Set<String> areaCodes = new HashSet<>();
        for (EObject area : many(root, mm.frameworkAreas())) {
            String code = str(area, mm.areaCode());
            if (code == null || code.isBlank()) {
                out.add(error("BLANK_ATTRIBUTE", "Area code must not be blank", label(area)));
            } else if (!areaCodes.add(code)) {
                out.add(error("DUPLICATE_AREA_CODE",
                        "Area code '" + code + "' is defined more than once", label(area)));
            }
            if (isBlank(area, mm.areaName())) {
                out.add(error("BLANK_ATTRIBUTE", "Area name must not be blank", label(area)));
            }
            competencies.addAll(many(area, mm.areaCompetencies()));
        }
        return competencies;
    }

    private void checkCompetencyCodes(List<EObject> competencies, List<ConformanceViolation> out) {
        Set<String> codes = new HashSet<>();
        for (EObject competency : competencies) {
            String code = str(competency, mm.competencyCode());
            if (code == null || code.isBlank()) {
                out.add(error("BLANK_ATTRIBUTE", "Competency code must not be blank", label(competency)));
            } else if (!codes.add(code)) {
                out.add(error("DUPLICATE_COMPETENCY_CODE",
                        "Competency code '" + code + "' is defined more than once (codes are framework-wide identifiers)",
                        label(competency)));
            }
            if (isBlank(competency, mm.competencyName())) {
                out.add(error("BLANK_ATTRIBUTE", "Competency name must not be blank", label(competency)));
            }
        }
    }

    private void checkCrossReferences(EObject root, List<EObject> competencies, List<ConformanceViolation> out) {
        for (EObject competency : competencies) {
            for (EObject prerequisite : many(competency, mm.competencyPrerequisites())) {
                if (prerequisite == competency) {
                    out.add(error("SELF_PREREQUISITE",
                            "A competency cannot be its own prerequisite", label(competency)));
                } else if (EcoreUtil.getRootContainer(prerequisite) != root) {
                    out.add(error("FOREIGN_REFERENCE",
                            "Prerequisite reference points outside this framework model", label(competency)));
                }
            }
            for (EObject descriptor : many(competency, mm.competencyLevelDescriptors())) {
                Object level = descriptor.eGet(mm.levelDescriptorLevel());
                if (level instanceof EObject levelObject && EcoreUtil.getRootContainer(levelObject) != root) {
                    out.add(error("FOREIGN_LEVEL",
                            "Level descriptor references a proficiency level outside this framework model",
                            label(competency)));
                }
            }
        }
    }

    private void checkAcyclicPrerequisites(List<EObject> competencies, List<ConformanceViolation> out) {
        Map<EObject, Integer> state = new IdentityHashMap<>();
        for (EObject competency : competencies) {
            if (state.getOrDefault(competency, 0) == 0
                    && depthFirstSearch(competency, state, new ArrayDeque<>(), out)) {
                return; // first cycle is sufficient grounds for rejection
            }
        }
    }

    private boolean depthFirstSearch(EObject node, Map<EObject, Integer> state,
                                     Deque<EObject> path, List<ConformanceViolation> out) {
        state.put(node, 1);
        path.addLast(node);
        for (EObject next : many(node, mm.competencyPrerequisites())) {
            if (next == node) {
                continue; // reported separately as SELF_PREREQUISITE
            }
            int nextState = state.getOrDefault(next, 0);
            if (nextState == 1) {
                out.add(error("CYCLIC_PREREQUISITES",
                        "Prerequisite cycle detected: " + cyclePath(path, next), label(next)));
                return true;
            }
            if (nextState == 0 && depthFirstSearch(next, state, path, out)) {
                return true;
            }
        }
        path.removeLast();
        state.put(node, 2);
        return false;
    }

    private String cyclePath(Deque<EObject> path, EObject closingNode) {
        StringJoiner joiner = new StringJoiner(" -> ");
        boolean inCycle = false;
        for (EObject node : path) {
            if (node == closingNode) {
                inCycle = true;
            }
            if (inCycle) {
                joiner.add(str(node, mm.competencyCode()));
            }
        }
        joiner.add(str(closingNode, mm.competencyCode()));
        return joiner.toString();
    }

    // ---------------------------------------------------------------- helpers

    private boolean isBlank(EObject object, EAttribute attribute) {
        String value = str(object, attribute);
        return value == null || value.isBlank();
    }

    private String str(EObject object, EAttribute attribute) {
        Object value = object.eGet(attribute);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<EObject> many(EObject owner, EReference reference) {
        return (List<EObject>) owner.eGet(reference);
    }

    private String label(EObject object) {
        var codeFeature = object.eClass().getEStructuralFeature("code");
        Object code = codeFeature == null ? null : object.eGet(codeFeature);
        return object.eClass().getName() + (code == null ? "" : " '" + code + "'");
    }
}
