package ie.ul.egas.gapanalysis.domain;

import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.domain.model.GapReportSummary;
import ie.ul.egas.learner.api.LearnerId;

import java.util.List;
import java.util.Optional;

/**
 * Driven port for stored gap reports (ADR-021). Declared in the domain, implemented by an adapter;
 * the domain neither knows nor cares that the current adapter is JPA over PostgreSQL. This is the
 * second port in this context, beside {@link CompetencyModelProjectionRepository}, and the two are
 * deliberately unalike: that one holds derived rows rebuilt by replaying an event, this one holds
 * authored history that no replay could reconstruct.
 *
 * <p><b>There is no update and no delete.</b> A {@link GapReport} has no mutators — it is a record
 * of an instant — so the only writes are the first one and, harmlessly, a repeat of it. Correcting a
 * report means generating a new one, which is what makes historical comparison possible at all;
 * ADR-021 accepts the storage growth that follows as the cost of reproducibility.
 *
 * <p>{@link #findSummariesForLearner} exists so that listing a learner's history never loads gap
 * graphs, the same column-only projection discipline that keeps assertion graphs off the profile
 * listing. A caller reads summaries to choose, then {@link #findById} to see one whole.
 */
public interface GapReportRepository {

    /**
     * Stores a report. Returns the argument rather than a re-read: nothing is generated or defaulted
     * by the store, so a returned copy would differ from what was passed only in ways that would be
     * a defect.
     */
    GapReport save(GapReport report);

    /**
     * One report, whole — every finding, with its target, attainment and provenance.
     *
     * <p>A report is only ever useful complete, since a finding without its evidence is the very
     * thing ADR-021 rejected, so this loads the graph in one query rather than lazily.
     */
    Optional<GapReport> findById(GapReportId id);

    /** A learner's reports, newest first, as metadata only. Empty for a learner with none. */
    List<GapReportSummary> findSummariesForLearner(LearnerId learnerId);
}
