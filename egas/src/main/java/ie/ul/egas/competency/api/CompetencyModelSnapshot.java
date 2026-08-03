package ie.ul.egas.competency.api;

import java.util.List;

/**
 * A competency model compiled into a flat, self-contained form for consumers outside this context
 * (ADR-007).
 *
 * <p><b>No EMF type appears here, by rule and by necessity.</b> ADR-012 confines
 * {@code org.eclipse.emf} to Competency Modelling and forbids an {@code EObject} from crossing the
 * module boundary, so the M1 graph is interpreted <em>inside</em> this module and published as
 * records. That is also what "compiled" means in ADR-007: a consumer reads rows rather than
 * traversing a model it would need EMF to understand.
 *
 * <p><b>The model supplies available levels, never a requirement.</b> {@code definedLevelCodes}
 * lists the levels for which a competency has a descriptor — what the framework says the competency
 * <em>means</em> at each level. The metamodel has no notion of a level anyone is obliged to reach,
 * so gap analysis compares attainment against a target supplied by the analysis request rather than
 * against an intrinsic requirement (ADR-021).
 */
public record CompetencyModelSnapshot(
        String frameworkName,
        String frameworkVersion,
        List<Level> levels,
        List<Competency> competencies) {

    public CompetencyModelSnapshot {
        levels = levels == null ? List.of() : List.copyOf(levels);
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
    }

    /** One position on the framework's proficiency scale. */
    public record Level(String code, String name, int ordinal) {
    }

    /**
     * One competency, carrying the identity derived from its framework and code
     * (ADR-019 Amendment 1) so a consumer can join to it without reading this context's tables.
     */
    public record Competency(
            CompetencyId id,
            String code,
            String name,
            String areaCode,
            List<String> definedLevelCodes) {

        public Competency {
            definedLevelCodes = definedLevelCodes == null ? List.of() : List.copyOf(definedLevelCodes);
        }
    }
}
