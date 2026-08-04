package ie.ul.egas.gapanalysis.domain.policy;

import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;

/**
 * Domain service contract: decide how serious a finding is (ADR-021).
 *
 * <p><b>A port rather than a method on the aggregate</b>, for the reason ADR-021 gives: severity is
 * the one genuine judgement in a context that is otherwise bookkeeping, so it is the only thing
 * whose variation is interesting. Ordinal distance is an obvious default and far from the only
 * defensible rule — an institution may treat any absence of evidence as more serious than a
 * one-level shortfall, or weight gaps by the confidence of the evidence behind them. Substituting
 * that judgement should touch neither the aggregate, nor storage, nor the API.
 *
 * <p>This is the third independent use of the substitutable-strategy technique in EGAS, after
 * ADR-006 for recommendation and ADR-018 for level resolution, which is what makes it an
 * architectural property of the system rather than an accommodation adopted once.
 *
 * <p><b>Two methods, not one taking a nullable or optional attainment.</b> Absence is a genuinely
 * different question — "how far short is this learner" versus "what does it mean that nothing has
 * been measured" — and splitting it makes an implementer answer both. A single method would let a
 * policy fall into treating unassessed as a shortfall from zero, which is exactly the collapse
 * ADR-021 forbids.
 */
public interface GapSeverityPolicy {

    /** How serious the shortfall is, given what the learner was held to have attained. */
    GapSeverity severityFor(AnalysisTarget target, AttainmentSnapshot attainment);

    /**
     * How serious it is that nothing has been measured for this competency at all.
     *
     * <p>Note there is no attainment to reason from. A policy answering this is judging the absence
     * of evidence, not a distance.
     */
    GapSeverity severityForUnassessed(AnalysisTarget target);
}
