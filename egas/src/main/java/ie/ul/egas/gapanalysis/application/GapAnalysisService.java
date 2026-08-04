package ie.ul.egas.gapanalysis.application;

import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.GapReportRepository;
import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.CompetencyModelNotProjectedException;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.gapanalysis.domain.model.ForbiddenLearnerScopeException;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.domain.model.GapReportNotFoundException;
import ie.ul.egas.gapanalysis.domain.model.GapReportSummary;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.gapanalysis.domain.model.UnknownTargetLevelException;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.learner.api.AttainedCompetency;
import ie.ul.egas.learner.api.LearnerAttainmentQuery;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.api.LearnerIdentityQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for gap analysis: the transaction boundary, the anti-corruption layer between
 * two published contracts and this context's own model, and the place ownership is enforced.
 *
 * <p><b>It holds no judgement of its own.</b> How serious a gap is belongs to
 * {@link GapSeverityPolicy}; what a finding is made of and what a report may contain belong to the
 * aggregate. What is left here is orchestration and one stated default — a competency with no
 * requested target is measured against the highest level for which it has a descriptor (ADR-021).
 *
 * <p><b>Identity arrives as data, never from ambient state.</b> Every method takes the caller's
 * subject as an explicit argument (ADR-016); nothing reads {@code SecurityContextHolder}, which the
 * {@code applicationStaysOutOfAdapters} fitness function enforces. The payoff is that the whole
 * ownership matrix below is exercisable with no security context, no token and no Spring container,
 * which {@code GapAnalysisServiceTests} demonstrates rather than asserts.
 *
 * <p><b>Two different denials, deliberately.</b> Operations scoped by a caller-supplied
 * {@code learnerId} refuse outright with {@link ForbiddenLearnerScopeException}, because no lookup
 * happens and the refusal discloses nothing. Reading a report by its identifier answers
 * {@link GapReportNotFoundException} whether it is absent or forbidden, because there a lookup did
 * happen and "forbidden" would confirm the report exists — the non-disclosure rule of ADR-015
 * Amendment 1, applied where its reasoning actually holds.
 *
 * <p><b>Snapshots are taken here.</b> {@link AttainedCompetency} is Learner Profiling's published
 * shape; {@link AttainmentSnapshot} is this context's. Copying between them at this boundary is what
 * ADR-021's snapshot rule requires — a report that held the producer's types would have its
 * historical shape governed by that contract's evolution.
 */
@Service
public class GapAnalysisService {

    private final CompetencyModelProjectionRepository models;
    private final LearnerAttainmentQuery attainments;
    private final LearnerIdentityQuery learnerIdentity;
    private final GapReportRepository reports;
    private final GapSeverityPolicy severityPolicy;
    private final Clock clock;

    public GapAnalysisService(CompetencyModelProjectionRepository models,
                              LearnerAttainmentQuery attainments,
                              LearnerIdentityQuery learnerIdentity,
                              GapReportRepository reports,
                              GapSeverityPolicy severityPolicy,
                              Clock clock) {
        this.models = models;
        this.attainments = attainments;
        this.learnerIdentity = learnerIdentity;
        this.reports = reports;
        this.severityPolicy = severityPolicy;
        this.clock = clock;
    }

    /**
     * Computes and stores one report.
     *
     * <p>The model is read from this context's projection and attainment from Learner Profiling's
     * synchronous contract — the asymmetry ADR-022 decided, and the reason no EMF graph is traversed
     * on this path. A learner with no profile yields attainments that are empty, so every competency
     * comes back unassessed, which is the correct answer rather than an error.
     *
     * @throws ForbiddenLearnerScopeException      if the caller may not analyse this learner
     * @throws CompetencyModelNotProjectedException if no projection exists for the framework
     * @throws UnknownTargetLevelException          if a requested target names no level of the framework
     */
    @Transactional
    public GapReport analyse(AnalyseGapCommand command) {
        requireScope(command.learnerId(), command.authSubject(), command.callerMayAnalyseAnyLearner());

        ProjectedCompetencyModel model = models.findByFrameworkId(command.frameworkId())
                .orElseThrow(() -> new CompetencyModelNotProjectedException(command.frameworkId()));

        Map<CompetencyId, AttainedCompetency> attained = attainmentsByCompetency(command.learnerId());

        List<SkillGap> gaps = new ArrayList<>();
        for (ProjectedCompetency competency : model.competencies()) {
            Optional<AnalysisTarget> target = targetFor(competency, model, command.targetLevelCodes());
            if (target.isEmpty()) {
                // Nothing this competency could be measured against: the model gives it no level
                // descriptor and the request named none. Inventing a target would fabricate the
                // very requirement ADR-021 records the metamodel does not state.
                continue;
            }
            AttainedCompetency attainment = attained.get(competency.id());
            gaps.add(attainment == null
                    ? SkillGap.unassessed(target.get(), severityPolicy)
                    : SkillGap.assess(target.get(), toSnapshot(attainment), severityPolicy));
        }

        return reports.save(GapReport.generate(
                command.learnerId(), command.frameworkId(), gaps, clock));
    }

    /**
     * One stored report, subject to ownership. Denial and absence are the same answer here — see the
     * class javadoc for why this method differs from the two scoped by a learner identifier.
     */
    @Transactional(readOnly = true)
    public GapReport getReportForReader(GapReportId id, String authSubject, boolean callerMayReadAny) {
        GapReport report = reports.findById(id)
                .orElseThrow(() -> GapReportNotFoundException.forId(id));

        if (!callerMayReadAny && !isCaller(report.learnerId(), authSubject)) {
            throw GapReportNotFoundException.forId(id);
        }
        return report;
    }

    /**
     * A learner's report history, metadata only. Gap graphs are never loaded on this path — the
     * projection discipline inherited from the profile and framework listings.
     *
     * @throws ForbiddenLearnerScopeException if the caller may not read this learner's history
     */
    @Transactional(readOnly = true)
    public List<GapReportSummary> listReportsForLearner(LearnerId learnerId, String authSubject,
                                                        boolean callerMayReadAny) {
        requireScope(learnerId, authSubject, callerMayReadAny);
        return reports.findSummariesForLearner(learnerId);
    }

    // --- ownership ---------------------------------------------------------------------------

    private void requireScope(LearnerId learnerId, String authSubject, boolean callerMayActForAny) {
        if (!callerMayActForAny && !isCaller(learnerId, authSubject)) {
            throw new ForbiddenLearnerScopeException(learnerId);
        }
    }

    /**
     * Whether the caller <em>is</em> the given learner.
     *
     * <p>The comparison a gap report cannot make for itself: it holds a {@link LearnerId} and no
     * subject, by ADR-021's design, so the correspondence is resolved through Learner Profiling's
     * published contract (ADR-017 Amendment 1). A caller with no profile owns nothing, which is the
     * right answer rather than an error — authentication and enrolment are different events.
     */
    private boolean isCaller(LearnerId learnerId, String authSubject) {
        return learnerIdentity.learnerIdFor(authSubject)
                .filter(learnerId::equals)
                .isPresent();
    }

    // --- analysis ----------------------------------------------------------------------------

    /**
     * Attainment keyed by competency, which works only because identity is derived from
     * {@code (frameworkId, code)} under ADR-019 Amendment 1: an assertion against another framework
     * carries an identifier no competency in this model can equal, so the map is framework-scoped
     * without a filter. No merge function is supplied — {@code uq_assertion_profile_competency}
     * makes a duplicate impossible, so one would be a defect worth failing on rather than resolving
     * arbitrarily.
     */
    private Map<CompetencyId, AttainedCompetency> attainmentsByCompetency(LearnerId learnerId) {
        return attainments.attainmentsFor(learnerId).stream()
                .collect(Collectors.toMap(AttainedCompetency::competencyId, Function.identity()));
    }

    /**
     * What this competency is measured against, or empty when nothing can be.
     *
     * <p>A requested level is validated against the <em>framework's</em> scale rather than the
     * competency's descriptors, because a target is what the analysis asks for and not what the
     * model demands: measuring against L4 a competency whose descriptors stop at L2 is a legitimate
     * question about a role. Absent a request, the default is the highest level the competency
     * actually describes — the stated default of ADR-021, not a discovered requirement.
     */
    private Optional<AnalysisTarget> targetFor(ProjectedCompetency competency,
                                               ProjectedCompetencyModel model,
                                               Map<String, String> requestedTargets) {
        String requested = requestedTargets.get(competency.code());
        Optional<ProjectedLevel> level = requested == null
                ? highestDescribedLevel(competency, model)
                : Optional.of(model.levelByCode(requested).orElseThrow(
                        () -> new UnknownTargetLevelException(competency.code(), requested)));

        return level.map(target -> new AnalysisTarget(
                competency.id(), competency.code(), competency.name(),
                target.code(), target.ordinal()));
    }

    private Optional<ProjectedLevel> highestDescribedLevel(ProjectedCompetency competency,
                                                          ProjectedCompetencyModel model) {
        return competency.definedLevelCodes().stream()
                .map(model::levelByCode)
                .flatMap(Optional::stream)
                .max(ProjectedLevel::compareTo);
    }

    // --- anti-corruption ---------------------------------------------------------------------

    private AttainmentSnapshot toSnapshot(AttainedCompetency attainment) {
        return new AttainmentSnapshot(
                attainment.attainedOrdinal(),
                attainment.attainedLevelCode(),
                attainment.resolvedAt(),
                attainment.evidence().stream().map(this::toSnapshot).toList());
    }

    private EvidenceSnapshot toSnapshot(AttainedCompetency.EvidenceSummary evidence) {
        return new EvidenceSnapshot(
                evidence.type(),
                evidence.claimedOrdinal(),
                evidence.claimedLevelCode(),
                evidence.confidence(),
                evidence.source(),
                evidence.recordedAt());
    }
}
