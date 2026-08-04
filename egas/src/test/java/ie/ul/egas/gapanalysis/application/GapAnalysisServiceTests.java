package ie.ul.egas.gapanalysis.application;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.GapFixtures;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.GapReportRepository;
import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.CompetencyModelNotProjectedException;
import ie.ul.egas.gapanalysis.domain.model.ForbiddenLearnerScopeException;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.domain.model.GapReportNotFoundException;
import ie.ul.egas.gapanalysis.domain.model.GapReportSummary;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.gapanalysis.domain.model.UnknownTargetLevelException;
import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.gapanalysis.domain.policy.OrdinalDistanceSeverityPolicy;
import ie.ul.egas.learner.api.AttainedCompetency;
import ie.ul.egas.learner.api.LearnerAttainmentQuery;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.api.LearnerIdentityQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The use case and the whole ownership matrix, with <b>no security infrastructure whatsoever</b> —
 * no security context, no token, no Spring container, no database. That is the ADR-016 payoff made
 * concrete: because the caller's subject is a command field rather than ambient state, authorisation
 * is testable as ordinary code, and because both cross-context reads are ports, the analysis itself
 * runs against stubs.
 *
 * <p>The stubs are hand-written rather than mocked, for the reason the rest of this suite is: a stub
 * that answers questions is readable at the call site, whereas a mock's expectations describe the
 * test framework more than the system.
 */
class GapAnalysisServiceTests {

    private static final String CALLER = "learner-subject";
    private static final String EDUCATOR = "educator-subject";
    private static final CompetencyFrameworkId FRAMEWORK = GapFixtures.FRAMEWORK;
    private static final LearnerId LEARNER = GapFixtures.LEARNER;
    private static final LearnerId OTHER_LEARNER = LearnerId.random();

    private static final CompetencyId DESIGN = CompetencyId.forCompetency(FRAMEWORK, "SE-DSN");
    private static final CompetencyId TESTING = CompetencyId.forCompetency(FRAMEWORK, "SE-TST");
    private static final CompetencyId ARCHITECTURE = CompetencyId.forCompetency(FRAMEWORK, "SE-ARC");

    private final StubProjection projection = new StubProjection();
    private final StubAttainments attainments = new StubAttainments();
    private final StubIdentities identities = new StubIdentities();
    private final RecordingReports reports = new RecordingReports();

    private GapAnalysisService service;

    @BeforeEach
    void setUp() {
        service = newService(new OrdinalDistanceSeverityPolicy());
        projection.model = model();
        identities.subjectToLearner.put(CALLER, LEARNER);
    }

    private GapAnalysisService newService(GapSeverityPolicy policy) {
        return new GapAnalysisService(projection, attainments, identities, reports, policy,
                GapFixtures.FIXED_CLOCK);
    }

    // --- analysis ------------------------------------------------------------------------------

    @Test
    void measuresEveryCompetencyTheModelDescribesAgainstItsHighestDescribedLevel() {
        // The stated default of ADR-021: absent a requested target, the highest level for which the
        // competency has a descriptor. SE-ARC describes none and is therefore not analysable at all.
        GapReport report = service.analyse(ownAnalysis(Map.of()));

        assertThat(report.gaps()).extracting(gap -> gap.target().competencyCode())
                .containsExactlyInAnyOrder("SE-DSN", "SE-TST");
        assertThat(report.gapFor(DESIGN).orElseThrow().target().targetLevelCode()).isEqualTo("L3");
        assertThat(report.gapFor(TESTING).orElseThrow().target().targetLevelCode()).isEqualTo("L1");
        assertThat(report.gapFor(ARCHITECTURE))
                .as("a competency the model describes at no level cannot be measured against one")
                .isEmpty();
    }

    @Test
    void aRequestedTargetOverridesTheDefault() {
        GapReport report = service.analyse(ownAnalysis(Map.of("SE-DSN", "L1")));

        AnalysisTarget target = report.gapFor(DESIGN).orElseThrow().target();
        assertThat(target.targetLevelCode()).isEqualTo("L1");
        assertThat(target.targetOrdinal()).isEqualTo(1);
        assertThat(report.gapFor(TESTING).orElseThrow().target().targetLevelCode())
                .as("competencies absent from the request keep the default")
                .isEqualTo("L1");
    }

    @Test
    void aTargetMayExceedTheLevelsACompetencyDescribes() {
        // A target is what the analysis asks for, not what the model demands (ADR-021). SE-TST
        // describes only L1; measuring it against L3 is a legitimate question about a role.
        GapReport report = service.analyse(ownAnalysis(Map.of("SE-TST", "L3")));

        assertThat(report.gapFor(TESTING).orElseThrow().target().targetOrdinal()).isEqualTo(3);
    }

    @Test
    void aTargetTheFrameworkDoesNotDefineIsRefusedRatherThanIgnored() {
        // Skipping silently would hand back a shorter report with nothing to indicate a competency
        // had been dropped — the failure mode hardest to notice afterwards.
        assertThatThrownBy(() -> service.analyse(ownAnalysis(Map.of("SE-DSN", "L9"))))
                .isInstanceOf(UnknownTargetLevelException.class)
                .hasMessageContaining("L9")
                .hasMessageContaining("SE-DSN");
    }

    @Test
    void carriesAttainmentAndItsEvidenceIntoTheFinding() {
        attainments.byLearner.put(LEARNER, List.of(attained(DESIGN, 1, "L1",
                new AttainedCompetency.EvidenceSummary("CERTIFICATION", 1, "L1", 0.9,
                        "ISTQB Foundation, 2025", GapFixtures.NOW))));

        SkillGap gap = service.analyse(ownAnalysis(Map.of())).gapFor(DESIGN).orElseThrow();

        assertThat(gap.attainment()).isPresent()
                .get().extracting(AttainmentSnapshot::attainedLevelCode).isEqualTo("L1");
        assertThat(gap.shortfall()).hasValue(2);
        assertThat(gap.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.type()).isEqualTo("CERTIFICATION");
            assertThat(evidence.confidence()).isEqualTo(0.9);
            assertThat(evidence.source()).isEqualTo("ISTQB Foundation, 2025");
            assertThat(evidence.recordedAt()).isEqualTo(GapFixtures.NOW);
        });
    }

    @Test
    void aCompetencyWithNoAttainmentIsUnassessedRatherThanAttainedAtZero() {
        attainments.byLearner.put(LEARNER, List.of(attained(DESIGN, 1, "L1")));

        GapReport report = service.analyse(ownAnalysis(Map.of()));

        assertThat(report.gapFor(DESIGN).orElseThrow().isUnassessed()).isFalse();
        assertThat(report.gapFor(TESTING).orElseThrow().isUnassessed()).isTrue();
        assertThat(report.unassessedGaps()).hasSize(1);
    }

    @Test
    void aLearnerWithNoProfileHasAttainedNothingRatherThanFailing() {
        // Authentication and enrolment are different events (ADR-017), and the attainment contract
        // documents an empty answer for a learner with no profile.
        GapReport report = service.analyse(new AnalyseGapCommand(
                EDUCATOR, OTHER_LEARNER, FRAMEWORK, Map.of(), true));

        assertThat(report.gaps()).hasSize(2);
        assertThat(report.unassessedGaps()).hasSize(2);
    }

    @Test
    void ignoresAttainmentRecordedAgainstAnotherFramework() {
        // Identity is derived from (framework, code) under ADR-019 Amendment 1, so an assertion from
        // another framework carries an id no competency in this model can equal. Without the derived
        // identity the two sides could not have been matched at all.
        CompetencyFrameworkId otherFramework = CompetencyFrameworkId.random();
        attainments.byLearner.put(LEARNER, List.of(
                attained(CompetencyId.forCompetency(otherFramework, "SE-DSN"), 3, "L3")));

        GapReport report = service.analyse(ownAnalysis(Map.of()));

        assertThat(report.gapFor(DESIGN).orElseThrow().isUnassessed()).isTrue();
    }

    @Test
    void severityComesFromTheInjectedPolicy() {
        GapAnalysisService alwaysMajor = newService(new GapSeverityPolicy() {
            @Override
            public GapSeverity severityFor(AnalysisTarget target, AttainmentSnapshot attainment) {
                return GapSeverity.MAJOR;
            }

            @Override
            public GapSeverity severityForUnassessed(AnalysisTarget target) {
                return GapSeverity.MAJOR;
            }
        });
        attainments.byLearner.put(LEARNER, List.of(attained(DESIGN, 3, "L3")));

        GapReport report = alwaysMajor.analyse(ownAnalysis(Map.of()));

        assertThat(report.gapFor(DESIGN).orElseThrow().severity())
                .as("a met target still answers MAJOR, so the service cannot be deciding severity")
                .isEqualTo(GapSeverity.MAJOR);
        assertThat(report.gapsOfSeverity(GapSeverity.MAJOR)).hasSize(2);
    }

    @Test
    void stampsTheReportFromTheInjectedClockAndStoresIt() {
        GapReport report = service.analyse(ownAnalysis(Map.of()));

        assertThat(report.generatedAt()).isEqualTo(GapFixtures.NOW);
        assertThat(reports.saved).containsExactly(report);
    }

    @Test
    void refusesAnalysisAgainstAFrameworkWithNoProjection() {
        // Two causes, indistinguishable from here: no such framework, or one registered so recently
        // that its projection has not been written (ADR-007's accepted lag).
        projection.model = null;

        assertThatThrownBy(() -> service.analyse(ownAnalysis(Map.of())))
                .isInstanceOf(CompetencyModelNotProjectedException.class)
                .hasMessageContaining(FRAMEWORK.value().toString());
        assertThat(reports.saved).isEmpty();
    }

    // --- ownership -----------------------------------------------------------------------------

    @Test
    void aLearnerMayAnalyseTheirOwnProfile() {
        assertThat(service.analyse(ownAnalysis(Map.of())).learnerId()).isEqualTo(LEARNER);
    }

    @Test
    void aLearnerMayNotAnalyseAnotherLearner() {
        assertThatThrownBy(() -> service.analyse(
                new AnalyseGapCommand(CALLER, OTHER_LEARNER, FRAMEWORK, Map.of(), false)))
                .isInstanceOf(ForbiddenLearnerScopeException.class);
        assertThat(reports.saved)
                .as("refusal happens before anything is computed or stored")
                .isEmpty();
    }

    @Test
    void aCallerWithNoProfileOwnsNothing() {
        // A valid token does not imply enrolment, so an unenrolled caller is not the learner they
        // name — even if they name the right identifier by chance.
        assertThatThrownBy(() -> service.analyse(
                new AnalyseGapCommand("stranger", LEARNER, FRAMEWORK, Map.of(), false)))
                .isInstanceOf(ForbiddenLearnerScopeException.class);
    }

    @Test
    void aCallerPermittedToAnalyseAnyLearnerNeedsNoProfileOfTheirOwn() {
        // The educator case. The security layer resolved the role question; this layer enforces the
        // answer without knowing the vocabulary it was reached with (ADR-015 Amendment 1).
        GapReport report = service.analyse(
                new AnalyseGapCommand(EDUCATOR, OTHER_LEARNER, FRAMEWORK, Map.of(), true));

        assertThat(report.learnerId()).isEqualTo(OTHER_LEARNER);
    }

    @Test
    void aLearnerMayReadTheirOwnReport() {
        GapReport stored = service.analyse(ownAnalysis(Map.of()));

        assertThat(service.getReportForReader(stored.id(), CALLER, false)).isEqualTo(stored);
    }

    @Test
    void aLearnerReadingAnotherLearnersReportCannotTellItFromOneThatDoesNotExist() {
        // Non-disclosure (ADR-015 Amendment 1). Answering "forbidden" would confirm the identifier
        // names a real report — and a report discloses which learner it is about.
        GapReport stored = service.analyse(
                new AnalyseGapCommand(EDUCATOR, OTHER_LEARNER, FRAMEWORK, Map.of(), true));

        Throwable forbidden = catchThrowable(
                () -> service.getReportForReader(stored.id(), CALLER, false));
        Throwable absent = catchThrowable(
                () -> service.getReportForReader(GapReportId.random(), CALLER, false));

        assertThat(forbidden).isInstanceOf(GapReportNotFoundException.class);
        assertThat(absent).isInstanceOf(GapReportNotFoundException.class);
        assertThat(forbidden).hasSameClassAs(absent);
    }

    @Test
    void aCallerPermittedToReadAnyReportSeesAnotherLearnersReport() {
        GapReport stored = service.analyse(
                new AnalyseGapCommand(EDUCATOR, OTHER_LEARNER, FRAMEWORK, Map.of(), true));

        assertThat(service.getReportForReader(stored.id(), EDUCATOR, true)).isEqualTo(stored);
    }

    @Test
    void aLearnerMayListTheirOwnHistoryButNotAnothers() {
        service.analyse(ownAnalysis(Map.of()));

        assertThat(service.listReportsForLearner(LEARNER, CALLER, false)).hasSize(1);
        assertThatThrownBy(() -> service.listReportsForLearner(OTHER_LEARNER, CALLER, false))
                .as("a caller-supplied identifier is never looked up, so refusing discloses nothing")
                .isInstanceOf(ForbiddenLearnerScopeException.class);
    }

    // --- helpers -------------------------------------------------------------------------------

    private AnalyseGapCommand ownAnalysis(Map<String, String> targets) {
        return new AnalyseGapCommand(CALLER, LEARNER, FRAMEWORK, targets, false);
    }

    private AttainedCompetency attained(CompetencyId competencyId, int ordinal, String levelCode,
                                        AttainedCompetency.EvidenceSummary... evidence) {
        return new AttainedCompetency(competencyId, FRAMEWORK, ordinal, levelCode,
                GapFixtures.NOW, List.of(evidence));
    }

    /** L1–L3; SE-DSN described to L3, SE-TST to L1, SE-ARC described at no level at all. */
    private ProjectedCompetencyModel model() {
        return new ProjectedCompetencyModel(FRAMEWORK, "Fixture Framework", "1.0",
                GapFixtures.NOW, GapFixtures.NOW,
                List.of(new ProjectedLevel("L1", "Foundation", 1),
                        new ProjectedLevel("L2", "Intermediate", 2),
                        new ProjectedLevel("L3", "Advanced", 3)),
                List.of(new ProjectedCompetency(DESIGN, "SE-DSN", "Software Design", "DES",
                                List.of("L1", "L2", "L3")),
                        new ProjectedCompetency(TESTING, "SE-TST", "Software Testing", "QUA",
                                List.of("L1")),
                        new ProjectedCompetency(ARCHITECTURE, "SE-ARC", "Software Architecture",
                                "DES", List.of())));
    }

    private static final class StubProjection implements CompetencyModelProjectionRepository {
        private ProjectedCompetencyModel model;

        @Override
        public void project(ProjectedCompetencyModel model) {
            this.model = model;
        }

        @Override
        public Optional<ProjectedCompetencyModel> findByFrameworkId(CompetencyFrameworkId id) {
            return Optional.ofNullable(model).filter(held -> held.frameworkId().equals(id));
        }

        @Override
        public boolean existsForFramework(CompetencyFrameworkId id) {
            return findByFrameworkId(id).isPresent();
        }
    }

    private static final class StubAttainments implements LearnerAttainmentQuery {
        private final Map<LearnerId, List<AttainedCompetency>> byLearner = new HashMap<>();

        @Override
        public List<AttainedCompetency> attainmentsFor(LearnerId learnerId) {
            return byLearner.getOrDefault(learnerId, List.of());
        }
    }

    private static final class StubIdentities implements LearnerIdentityQuery {
        private final Map<String, LearnerId> subjectToLearner = new HashMap<>();

        @Override
        public Optional<LearnerId> learnerIdFor(String authSubject) {
            return Optional.ofNullable(subjectToLearner.get(authSubject));
        }
    }

    private static final class RecordingReports implements GapReportRepository {
        private final List<GapReport> saved = new ArrayList<>();

        @Override
        public GapReport save(GapReport report) {
            saved.add(report);
            return report;
        }

        @Override
        public Optional<GapReport> findById(GapReportId id) {
            return saved.stream().filter(report -> report.id().equals(id)).findFirst();
        }

        @Override
        public List<GapReportSummary> findSummariesForLearner(LearnerId learnerId) {
            return saved.stream()
                    .filter(report -> report.learnerId().equals(learnerId))
                    .map(report -> new GapReportSummary(report.id(), report.frameworkId(),
                            report.generatedAt(), report.gaps().size()))
                    .toList();
        }
    }
}
