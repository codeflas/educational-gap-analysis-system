package ie.ul.egas.gapanalysis.application;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.learner.api.LearnerId;

import java.util.Map;
import java.util.Objects;

/**
 * A request to compute one learner's gaps against one framework.
 *
 * <p><b>{@code authSubject} is who is asking; {@code learnerId} is who is being analysed.</b> They
 * are separate fields because they are separate questions, and an educator analysing a learner is a
 * legitimate case. Identity arrives as data (ADR-016) — the web adapter takes the subject from the
 * token and never from a request body, which is what keeps {@code learnerId} from being a way to
 * impersonate.
 *
 * <p><b>{@code targetLevelCodes} maps competency code to the level this analysis measures against.</b>
 * It exists because the metamodel states no required level: a target is a question about a role, a
 * curriculum or an intention, not a property of the framework (ADR-021). A competency absent from
 * the map defaults to the highest level for which it has a descriptor, which is a stated default
 * rather than a discovered requirement. Keys are competency <em>codes</em> rather than identifiers
 * because a caller composing an analysis has the framework's vocabulary in hand, not derived UUIDs.
 *
 * <p><b>{@code callerMayAnalyseAnyLearner} is a decision, not a role.</b> The security layer resolves
 * the role question and passes the answer; this module never learns the vocabulary it was reached
 * with, exactly as {@code callerMayReadAny} works for learner profiles (ADR-015 Amendment 1).
 */
public record AnalyseGapCommand(
        String authSubject,
        LearnerId learnerId,
        CompetencyFrameworkId frameworkId,
        Map<String, String> targetLevelCodes,
        boolean callerMayAnalyseAnyLearner) {

    public AnalyseGapCommand {
        Objects.requireNonNull(authSubject, "authSubject must not be null");
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        Objects.requireNonNull(frameworkId, "frameworkId must not be null");
        targetLevelCodes = targetLevelCodes == null ? Map.of() : Map.copyOf(targetLevelCodes);
    }

    /** An analysis with no explicit targets: every competency measured against its highest level. */
    public static AnalyseGapCommand forOwnProfile(String authSubject, LearnerId learnerId,
                                                  CompetencyFrameworkId frameworkId) {
        return new AnalyseGapCommand(authSubject, learnerId, frameworkId, Map.of(), false);
    }
}
