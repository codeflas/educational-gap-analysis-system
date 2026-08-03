package ie.ul.egas.competency.api;

import ie.ul.egas.shared.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a single competency element within a framework model. Downstream contexts
 * (Learner Profiling, Gap Analysis, Catalogue, Recommendation) refer to competencies solely
 * through this value object — never through model objects — preserving context autonomy.
 */
public record CompetencyId(UUID value) implements Identifier {

    public CompetencyId {
        Objects.requireNonNull(value, "CompetencyId requires a non-null UUID");
    }

    public static CompetencyId random() {
        return new CompetencyId(UUID.randomUUID());
    }

    /**
     * The identity of a competency, derived from the framework that defines it and its code
     * (ADR-019 Amendment 1).
     *
     * <p>The M2 metamodel identifies a competency by {@code code} alone and declares no identifier
     * attribute, so until this method existed nothing in the system ever minted a
     * {@code CompetencyId} that corresponded to a real competency: a value stored by another
     * context was not merely unvalidated but unmatchable. Deriving it needs no metamodel change,
     * leaving the ADR-003 freeze intact.
     *
     * <p>The derivation is stable — the same framework and code always yield the same value, so
     * re-registering or re-projecting a model re-keys nothing — and computable on both sides of a
     * context boundary, which is what a join key must be when neither side may read the other's
     * tables. Codes are unique framework-wide by the metamodel's well-formedness rules, so no
     * collision is possible within a framework; including the framework keeps two frameworks that
     * reuse a code apart.
     */
    public static CompetencyId forCompetency(CompetencyFrameworkId frameworkId, String code) {
        Objects.requireNonNull(frameworkId, "frameworkId must not be null");
        Objects.requireNonNull(code, "competency code must not be null");
        return new CompetencyId(UUID.nameUUIDFromBytes(
                (frameworkId.value() + ":" + code).getBytes(StandardCharsets.UTF_8)));
    }

    public static CompetencyId of(String raw) {
        return new CompetencyId(UUID.fromString(raw));
    }
}
