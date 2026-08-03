package ie.ul.egas.learner.domain.policy;

import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.EvidenceRecord;

import java.util.List;

/**
 * Domain service contract: collapse the evidence gathered for one competency into the single
 * proficiency level the learner is held to have attained (ADR-018).
 *
 * <p>A port rather than a method on the aggregate, for three reasons. It is the only genuine
 * business <em>policy</em> in a context that is otherwise bookkeeping, so it is the only thing
 * whose variation is interesting. It is the natural test seam — a lambda policy lets every
 * aggregate invariant be exercised without reasoning about resolution arithmetic, exactly as
 * {@code ConformanceValidator} did for the competency aggregate in Step 2. And it gives the
 * dissertation a second, independent instance of the substitutable-strategy technique that ADR-006
 * applies to recommendation, which is evidence for RQ3 that the pattern is an architectural
 * property of the system rather than a one-off accommodation of the LLM boundary.
 *
 * <p>Implementations must be deterministic for a given evidence set: two resolutions of the same
 * input must agree, or an assertion's stored level would depend on when it was written.
 */
@FunctionalInterface
public interface LevelResolutionPolicy {

    /**
     * @param evidence the complete evidence set for one competency, never null and never empty
     * @throws UnresolvableEvidenceException if no level can be determined
     */
    AttainedLevel resolve(List<EvidenceRecord> evidence);
}
