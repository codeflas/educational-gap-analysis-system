package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a gap report — the aggregate that holds a set of computed gaps.
 *
 * <p>Module-internal, in {@code domain.model} rather than the published {@code api} package,
 * following the same reasoning as {@code AssertionId} in Learner Profiling: no other context
 * addresses a report today. {@code SkillGapId} is published because its javadoc records that an
 * individual gap is "one element of a gap analysis result set" — the element was made addressable
 * across contexts, the container was not.
 *
 * <p>If Recommendation (ADR-006) turns out to consume whole reports rather than individual gaps,
 * this type is promoted to {@code api} unchanged. Publishing it now, before a consumer exists,
 * would widen the module's contract on speculation.
 */
public record GapReportId(UUID value) implements Identifier {

    public GapReportId {
        Objects.requireNonNull(value, "GapReportId requires a non-null UUID");
    }

    public static GapReportId random() {
        return new GapReportId(UUID.randomUUID());
    }

    public static GapReportId of(String raw) {
        return new GapReportId(UUID.fromString(raw));
    }
}
