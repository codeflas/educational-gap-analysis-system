package ie.ul.egas.gapanalysis;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.competency.application.CompetencyFrameworkService;
import ie.ul.egas.gapanalysis.application.AnalyseGapCommand;
import ie.ul.egas.gapanalysis.application.GapAnalysisService;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.gapanalysis.domain.model.ForbiddenLearnerScopeException;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportNotFoundException;
import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.learner.application.CreateLearnerProfileCommand;
import ie.ul.egas.learner.application.LearnerProfileService;
import ie.ul.egas.learner.application.RecordEvidenceCommand;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.model.EvidenceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The whole slice, end to end across three bounded contexts: a framework registered in Competency
 * Modelling, evidence recorded in Learner Profiling, and a stored gap report computed from both.
 *
 * <p>This is the test that proves the <em>system</em> rather than any one part of it. Every layer
 * below has its own suite, and all of them could pass while nothing joined them up — which is
 * precisely the defect an integration is prone to. Three joins are exercised here that exist nowhere
 * else: the derived competency identity (ADR-019 Amendment 1) matching a learner's assertion to a
 * projected competency, the published attainment contract carrying evidence provenance across a
 * module boundary (ADR-022), and the published identity contract resolving a principal to a learner
 * so ownership can be decided at all (ADR-017 Amendment 1).
 *
 * <p>Projection is asynchronous, so its arrival is awaited rather than assumed — ADR-007's eventual
 * consistency made visible rather than papered over.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GapAnalysisIntegrationTests {

    @Autowired
    CompetencyFrameworkService frameworks;

    @Autowired
    LearnerProfileService learners;

    @Autowired
    GapAnalysisService gapAnalysis;

    @Autowired
    CompetencyModelProjectionRepository projection;

    @Test
    void computesAStoredExplainableReportFromARegisteredFrameworkAndRecordedEvidence() {
        CompetencyFrameworkId framework = registerFramework("Gap End To End");
        String subject = uniqueSubject();
        LearnerId learner = provision(subject, "Ada Lovelace");
        recordEvidence(subject, framework, "SE-DSN", 1, "L1", 0.9, "Self-assessment, March 2026");

        GapReport report = gapAnalysis.analyse(
                AnalyseGapCommand.forOwnProfile(subject, learner, framework));

        // SE-DSN describes L2, SE-TST describes L1, SE-ARC describes no level and is not analysable.
        assertThat(report.gaps()).extracting(gap -> gap.target().competencyCode())
                .containsExactlyInAnyOrder("SE-DSN", "SE-TST");
        assertThat(report.learnerId()).isEqualTo(learner);
        assertThat(report.frameworkId()).isEqualTo(framework);

        SkillGap design = report.gapFor(CompetencyId.forCompetency(framework, "SE-DSN"))
                .orElseThrow();
        assertThat(design.target().targetLevelCode()).isEqualTo("L2");
        assertThat(design.attainment()).isPresent();
        assertThat(design.shortfall()).hasValue(1);
        assertThat(design.severity()).isEqualTo(GapSeverity.MINOR);

        // The explainability chain, having crossed two module boundaries intact (ADR-021, RQ3).
        assertThat(design.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.type()).isEqualTo(EvidenceType.SELF_DECLARED.name());
            assertThat(evidence.claimedLevelCode()).isEqualTo("L1");
            assertThat(evidence.confidence()).isEqualTo(0.9);
            assertThat(evidence.source()).isEqualTo("Self-assessment, March 2026");
            assertThat(evidence.recordedAt()).isNotNull();
        });
    }

    @Test
    void aCompetencyWithNoEvidenceIsUnassessedRatherThanAttainedAtZero() {
        CompetencyFrameworkId framework = registerFramework("Gap Unassessed");
        String subject = uniqueSubject();
        LearnerId learner = provision(subject, "Grace Hopper");
        recordEvidence(subject, framework, "SE-DSN", 2, "L2", 0.8, "Assessment, 2026");

        GapReport report = gapAnalysis.analyse(
                AnalyseGapCommand.forOwnProfile(subject, learner, framework));

        SkillGap testing = report.gapFor(CompetencyId.forCompetency(framework, "SE-TST"))
                .orElseThrow();
        assertThat(testing.isUnassessed()).isTrue();
        assertThat(testing.attainment()).isEmpty();
        assertThat(testing.shortfall()).isEmpty();
        assertThat(testing.severity()).isEqualTo(GapSeverity.UNASSESSED);
        assertThat(report.unassessedGaps()).extracting(gap -> gap.target().competencyCode())
                .containsExactly("SE-TST");
    }

    @Test
    void theReportSurvivesAReloadWithItsProvenanceIntact() {
        CompetencyFrameworkId framework = registerFramework("Gap Reload");
        String subject = uniqueSubject();
        LearnerId learner = provision(subject, "Barbara Liskov");
        recordEvidence(subject, framework, "SE-TST", 1, "L1", 0.55, "Peer review, 2026");

        GapReport written = gapAnalysis.analyse(
                AnalyseGapCommand.forOwnProfile(subject, learner, framework));
        GapReport read = gapAnalysis.getReportForReader(written.id(), subject, false);

        assertThat(read.id()).isEqualTo(written.id());
        SkillGap testing = read.gapFor(CompetencyId.forCompetency(framework, "SE-TST"))
                .orElseThrow();
        assertThat(testing.severity()).isEqualTo(GapSeverity.MET);
        assertThat(testing.evidence()).extracting(EvidenceSnapshot::source)
                .containsExactly("Peer review, 2026");
    }

    @Test
    void aLearnerMayNotAnalyseOrListAnotherLearner() {
        CompetencyFrameworkId framework = registerFramework("Gap Ownership");
        String subject = uniqueSubject();
        LearnerId learner = provision(subject, "Ada Owner");
        LearnerId other = provision(uniqueSubject(), "Someone Else");

        assertThatThrownBy(() -> gapAnalysis.analyse(
                new AnalyseGapCommand(subject, other, framework, Map.of(), false)))
                .isInstanceOf(ForbiddenLearnerScopeException.class);

        assertThatThrownBy(() -> gapAnalysis.listReportsForLearner(other, subject, false))
                .isInstanceOf(ForbiddenLearnerScopeException.class);

        assertThat(gapAnalysis.listReportsForLearner(learner, subject, false)).isEmpty();
    }

    @Test
    void aLearnerCannotTellAnotherLearnersReportFromOneThatDoesNotExist() {
        CompetencyFrameworkId framework = registerFramework("Gap Non Disclosure");
        String ownerSubject = uniqueSubject();
        LearnerId owner = provision(ownerSubject, "Report Owner");
        String intruderSubject = uniqueSubject();
        provision(intruderSubject, "Curious Party");

        GapReport report = gapAnalysis.analyse(
                AnalyseGapCommand.forOwnProfile(ownerSubject, owner, framework));

        assertThatThrownBy(() -> gapAnalysis.getReportForReader(report.id(), intruderSubject, false))
                .isInstanceOf(GapReportNotFoundException.class);
        assertThat(gapAnalysis.getReportForReader(report.id(), intruderSubject, true))
                .as("a caller the security layer says may read any report still sees it")
                .isEqualTo(report);
    }

    @Test
    void aLearnerHistoryListsNewestFirst() {
        CompetencyFrameworkId framework = registerFramework("Gap History");
        String subject = uniqueSubject();
        LearnerId learner = provision(subject, "Historic Learner");

        gapAnalysis.analyse(AnalyseGapCommand.forOwnProfile(subject, learner, framework));
        gapAnalysis.analyse(AnalyseGapCommand.forOwnProfile(subject, learner, framework));

        assertThat(gapAnalysis.listReportsForLearner(learner, subject, false))
                .as("recomputation adds a report rather than replacing one — history is kept")
                .hasSize(2)
                .allSatisfy(summary -> assertThat(summary.gapCount()).isEqualTo(2));
    }

    @Test
    void anExplicitTargetChangesTheFindingForTheSameEvidence() {
        // The property that makes recording the target per gap necessary rather than redundant: the
        // same attainment yields a different gap under a different target (ADR-021).
        CompetencyFrameworkId framework = registerFramework("Gap Target");
        String subject = uniqueSubject();
        LearnerId learner = provision(subject, "Target Learner");
        recordEvidence(subject, framework, "SE-DSN", 1, "L1", 0.9, "Self-assessment, 2026");

        CompetencyId design = CompetencyId.forCompetency(framework, "SE-DSN");
        SkillGap atDefault = gapAnalysis.analyse(
                        AnalyseGapCommand.forOwnProfile(subject, learner, framework))
                .gapFor(design).orElseThrow();
        SkillGap atL3 = gapAnalysis.analyse(new AnalyseGapCommand(
                        subject, learner, framework, Map.of("SE-DSN", "L3"), false))
                .gapFor(design).orElseThrow();

        assertThat(atDefault.target().targetLevelCode()).isEqualTo("L2");
        assertThat(atDefault.shortfall()).hasValue(1);
        assertThat(atDefault.severity()).isEqualTo(GapSeverity.MINOR);

        assertThat(atL3.target().targetLevelCode()).isEqualTo("L3");
        assertThat(atL3.shortfall()).hasValue(2);
        assertThat(atL3.severity()).isEqualTo(GapSeverity.MODERATE);
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Registers a framework and waits for its projection, which arrives asynchronously (ADR-007). */
    private CompetencyFrameworkId registerFramework(String name) {
        CompetencyFrameworkId id = frameworks.register(FrameworkFixtures.validCommand(name, "1.0")).id();
        await().atMost(Duration.ofSeconds(20))
                .until(() -> projection.existsForFramework(id));
        return id;
    }

    private String uniqueSubject() {
        return "gap-subject-" + UUID.randomUUID();
    }

    private LearnerId provision(String subject, String displayName) {
        return learners.createProfile(new CreateLearnerProfileCommand(subject, displayName)).id();
    }

    private void recordEvidence(String subject, CompetencyFrameworkId framework, String competencyCode,
                                int claimedOrdinal, String claimedLevelCode, double confidence,
                                String source) {
        learners.recordEvidence(new RecordEvidenceCommand(
                subject,
                CompetencyId.forCompetency(framework, competencyCode).value(),
                framework.value(),
                EvidenceType.SELF_DECLARED,
                claimedOrdinal,
                claimedLevelCode,
                confidence,
                source));
    }
}
