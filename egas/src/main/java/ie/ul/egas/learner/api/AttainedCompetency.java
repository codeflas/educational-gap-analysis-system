package ie.ul.egas.learner.api;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;

import java.time.Instant;
import java.util.List;

/**
 * What a learner is held to have attained for one competency, published for consumers outside
 * Learner Profiling (ADR-022).
 *
 * <p><b>Evidence travels with the level, not behind it.</b> ADR-021's explainability chain runs
 * analysis target → attainment → the observations that produced it, and that chain breaks at the
 * module boundary unless the contract carries the last link. A consumer receiving only a level
 * could report a gap but not defend one, which is precisely what RQ3 claims the system can do.
 *
 * <p>{@code type} is a plain string rather than the {@code EvidenceType} enum: that enum lives in
 * {@code learner.domain.model}, and {@code publishedContractsArePure} forbids a domain type in a
 * published contract. Flattening it here keeps the boundary honest at the cost of one lost
 * compile-time check on the consumer's side.
 */
public record AttainedCompetency(
        CompetencyId competencyId,
        CompetencyFrameworkId frameworkId,
        int attainedOrdinal,
        String attainedLevelCode,
        Instant resolvedAt,
        List<EvidenceSummary> evidence) {

    public AttainedCompetency {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /** One observation supporting the attainment, as recorded (ADR-018). */
    public record EvidenceSummary(
            String type,
            int claimedOrdinal,
            String claimedLevelCode,
            double confidence,
            String source,
            Instant recordedAt) {
    }
}
