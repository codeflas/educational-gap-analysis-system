package ie.ul.egas.gapanalysis.domain.policy;

import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;

/**
 * Default {@link GapSeverityPolicy}: severity is how many proficiency levels separate attainment
 * from the analysis target (ADR-021).
 *
 * <p>Ordinal distance is the obvious reading and deliberately a simple one. It treats every level
 * step as equally significant, which no real proficiency scale guarantees — the distance from
 * novice to competent is not the distance from competent to expert — and it ignores how well
 * evidenced an attainment is, so a confident assessment and a hesitant self-declaration at the same
 * level are judged alike. Both are acceptable in a default and are precisely the judgements ADR-021
 * expects an institution to substitute.
 *
 * <p><b>Exceeding the target is {@link GapSeverity#MET}, not a negative gap.</b> A learner beyond
 * what was asked for has no gap to remediate, and reporting surplus as though it were a finding
 * would give a recommender something to act on where there is nothing to do.
 *
 * <p><b>Unassessed is never a shortfall.</b> It reports {@link GapSeverity#UNASSESSED} regardless of
 * how high the target sits, because no distance has been measured — the learner may already be
 * beyond the target and simply have no evidence recorded. Treating the absence as a maximal gap
 * would send a recommender to propose learning when an assessment is what is missing.
 */
public final class OrdinalDistanceSeverityPolicy implements GapSeverityPolicy {

    private static final int MINOR_THRESHOLD = 1;
    private static final int MODERATE_THRESHOLD = 2;

    @Override
    public GapSeverity severityFor(AnalysisTarget target, AttainmentSnapshot attainment) {
        int shortfall = target.targetOrdinal() - attainment.attainedOrdinal();

        if (shortfall <= 0) {
            return GapSeverity.MET;
        }
        if (shortfall <= MINOR_THRESHOLD) {
            return GapSeverity.MINOR;
        }
        if (shortfall <= MODERATE_THRESHOLD) {
            return GapSeverity.MODERATE;
        }
        return GapSeverity.MAJOR;
    }

    @Override
    public GapSeverity severityForUnassessed(AnalysisTarget target) {
        return GapSeverity.UNASSESSED;
    }
}
