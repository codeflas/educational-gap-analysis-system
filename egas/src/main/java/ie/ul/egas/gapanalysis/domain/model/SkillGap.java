package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.gapanalysis.api.SkillGapId;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One finding: what a competency was measured against, what the learner was held to have attained,
 * the evidence behind that, and how serious the difference is judged to be (ADR-021).
 *
 * <p><b>Self-contained by construction.</b> Everything needed to explain this finding is captured
 * when it is made — target, attainment, provenance — rather than recovered afterwards from data
 * that has since moved. A gap computed in March must still be defensible in June after the
 * framework has been revised and further evidence recorded, which a finding holding references
 * rather than copies could not be.
 *
 * <p><b>Absence is absence.</b> A competency the learner has no attainment for holds no
 * {@link AttainmentSnapshot} at all — not a zero-ordinal one. The distinction survives into
 * {@link GapSeverity#UNASSESSED}, so a consumer can tell "nothing has been measured" from "measured
 * and far behind", which are different problems with different remedies.
 *
 * <p>Severity is supplied by a {@link GapSeverityPolicy} passed in at construction rather than
 * computed here — the double-dispatch idiom this codebase already uses for conformance validation
 * and level resolution, which keeps the one substitutable judgement out of the value that records
 * it.
 */
public final class SkillGap {

    private final SkillGapId id;
    private final AnalysisTarget target;
    private final AttainmentSnapshot attainment;
    private final GapSeverity severity;

    private SkillGap(SkillGapId id, AnalysisTarget target, AttainmentSnapshot attainment,
                     GapSeverity severity) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.attainment = attainment;
    }

    /** A finding for a competency the learner holds an attainment for. */
    public static SkillGap assess(AnalysisTarget target, AttainmentSnapshot attainment,
                                  GapSeverityPolicy policy) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(attainment, "attainment must not be null — use unassessed(...)");
        Objects.requireNonNull(policy, "policy must not be null");
        return new SkillGap(SkillGapId.random(), target, attainment,
                policy.severityFor(target, attainment));
    }

    /**
     * A finding for a competency nothing has been measured for.
     *
     * <p>A separate factory rather than a null attainment, so the absent case is chosen explicitly
     * at every call site instead of arising by omission.
     */
    public static SkillGap unassessed(AnalysisTarget target, GapSeverityPolicy policy) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        return new SkillGap(SkillGapId.random(), target, null,
                policy.severityForUnassessed(target));
    }

    /** Rehydrates a persisted finding. Severity is restored, never recomputed: re-running a policy
     * on load would silently rewrite a historical judgement (ADR-021). */
    public static SkillGap reconstitute(SkillGapId id, AnalysisTarget target,
                                        AttainmentSnapshot attainment, GapSeverity severity) {
        return new SkillGap(id, target, attainment, severity);
    }

    public SkillGapId id() {
        return id;
    }

    public AnalysisTarget target() {
        return target;
    }

    /** The learner's attainment, empty when nothing has been measured for this competency. */
    public Optional<AttainmentSnapshot> attainment() {
        return Optional.ofNullable(attainment);
    }

    public GapSeverity severity() {
        return severity;
    }

    /** True when nothing has been measured — distinct from having been measured at the bottom. */
    public boolean isUnassessed() {
        return attainment == null;
    }

    /**
     * How many levels short of the target the learner is, or empty when nothing was measured.
     *
     * <p>Empty rather than zero, and never negative: a learner beyond the target has a shortfall of
     * zero, which is a different statement from having no measurement at all.
     */
    public OptionalInt shortfall() {
        if (attainment == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(0, target.targetOrdinal() - attainment.attainedOrdinal()));
    }

    /** The observations behind the attainment — empty when nothing has been measured. */
    public List<EvidenceSnapshot> evidence() {
        return attainment == null ? List.of() : attainment.evidence();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof SkillGap that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "SkillGap[competency=%s, target=%s, severity=%s]"
                .formatted(target.competencyCode(), target.targetLevelCode(), severity);
    }
}
