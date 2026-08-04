package ie.ul.egas.gapanalysis.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.api.LearnerId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregate root: one learner measured against one framework at one moment (ADR-021).
 *
 * <p><b>Stored, and therefore stated as of an instant.</b> {@code generatedAt} is not decoration —
 * a report is a true record of when it was made and stops describing the present the moment
 * evidence changes. Nothing here tracks its inputs, and no invalidation is provided: recomputation
 * is an explicit act, for the same reason provisioning is in ADR-017. A consumer must read the
 * timestamp as significant.
 *
 * <p><b>Complete on its own.</b> Every gap carries its own target, attainment and provenance, so
 * Recommendation can synthesise a pathway without reaching back into Learner Profiling or
 * Competency Modelling. That is what keeps the module DAG acyclic and both contexts independently
 * extractable — a report that held references would have made the downstream consumer depend on
 * the upstream producers.
 *
 * <p>The framework and learner are referenced by identifier only, unvalidated (ADR-019): a report
 * may name a learner or framework that has since been removed, and remains readable if it does.
 */
public final class GapReport {

    private final GapReportId id;
    private final LearnerId learnerId;
    private final CompetencyFrameworkId frameworkId;
    private final Instant generatedAt;
    private final List<SkillGap> gaps;

    private GapReport(GapReportId id, LearnerId learnerId, CompetencyFrameworkId frameworkId,
                      Instant generatedAt, List<SkillGap> gaps) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.learnerId = Objects.requireNonNull(learnerId, "learnerId must not be null");
        this.frameworkId = Objects.requireNonNull(frameworkId, "frameworkId must not be null");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        this.gaps = List.copyOf(Objects.requireNonNull(gaps, "gaps must not be null"));
        requireOneFindingPerCompetency(this.gaps);
    }

    /**
     * One analysis produces at most one finding per competency.
     *
     * <p>Two findings for the same competency could disagree — different targets, different
     * severities — and a report holding both would have no defensible answer to "what is this
     * learner's gap in X". {@link #gapFor(CompetencyId)} would return whichever happened to be
     * first, so the ambiguity would surface as an arbitrary answer rather than as an error.
     * Enforced on reconstitution too: a stored report that violates this is corrupt, and loading it
     * quietly would carry the corruption into every consumer.
     */
    private static void requireOneFindingPerCompetency(List<SkillGap> gaps) {
        Set<CompetencyId> seen = new HashSet<>();
        for (SkillGap gap : gaps) {
            if (!seen.add(gap.target().competencyId())) {
                throw new IllegalArgumentException(
                        "A gap report holds at most one finding per competency, but competency "
                                + gap.target().competencyCode() + " appears more than once");
            }
        }
    }

    /**
     * Records the outcome of an analysis. The instant comes from the injected {@link Clock} so a
     * report's timestamp is deterministic under test rather than merely non-null.
     */
    public static GapReport generate(LearnerId learnerId, CompetencyFrameworkId frameworkId,
                                     List<SkillGap> gaps, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new GapReport(GapReportId.random(), learnerId, frameworkId, clock.instant(), gaps);
    }

    /**
     * Rehydrates a persisted report. Gaps are restored as stored, never recomputed — re-deriving a
     * historical judgement on load would defeat the reproducibility the snapshots exist for.
     */
    public static GapReport reconstitute(GapReportId id, LearnerId learnerId,
                                         CompetencyFrameworkId frameworkId, Instant generatedAt,
                                         List<SkillGap> gaps) {
        return new GapReport(id, learnerId, frameworkId, generatedAt, gaps);
    }

    public GapReportId id() { return id; }

    public LearnerId learnerId() { return learnerId; }

    public CompetencyFrameworkId frameworkId() { return frameworkId; }

    public Instant generatedAt() { return generatedAt; }

    /** The findings, unmodifiable: a report is a record and does not change after it is made. */
    public List<SkillGap> gaps() {
        return Collections.unmodifiableList(gaps);
    }

    /**
     * The finding for one competency, empty when the analysis did not cover it — which is not the
     * same as covering it and finding nothing to do (that is a gap of severity {@code MET}).
     */
    public Optional<SkillGap> gapFor(CompetencyId competencyId) {
        return gaps.stream()
                .filter(gap -> gap.target().competencyId().equals(competencyId))
                .findFirst();
    }

    /** Findings judged at the given severity — the grouping a recommender reads first. */
    public List<SkillGap> gapsOfSeverity(GapSeverity severity) {
        List<SkillGap> matching = new ArrayList<>();
        for (SkillGap gap : gaps) {
            if (gap.severity() == severity) {
                matching.add(gap);
            }
        }
        return List.copyOf(matching);
    }

    /**
     * Competencies nothing has been measured for.
     *
     * <p>Separated from unmet gaps deliberately: these call for assessment, not remediation, and a
     * recommender that proposed learning for them would be answering a question nobody asked.
     */
    public List<SkillGap> unassessedGaps() {
        return gaps.stream().filter(SkillGap::isUnassessed).toList();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof GapReport that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GapReport[id=%s, learner=%s, gaps=%d]"
                .formatted(id.value(), learnerId.value(), gaps.size());
    }
}
