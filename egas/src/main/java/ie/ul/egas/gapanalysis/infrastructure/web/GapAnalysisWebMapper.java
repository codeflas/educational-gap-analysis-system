package ie.ul.egas.gapanalysis.infrastructure.web;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.gapanalysis.application.AnalyseGapCommand;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportSummary;
import ie.ul.egas.gapanalysis.domain.model.SkillGap;
import ie.ul.egas.gapanalysis.infrastructure.web.dto.AnalyseGapRequest;
import ie.ul.egas.gapanalysis.infrastructure.web.dto.GapReportResponse;
import ie.ul.egas.gapanalysis.infrastructure.web.dto.GapReportSummaryResponse;
import ie.ul.egas.learner.api.LearnerId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hand-written web mapping, matching {@code LearnerWebMapper} and {@code FrameworkWebMapper} — an
 * annotation processor buys little at this size.
 *
 * <p><b>The subject and the role decision are separate parameters, never request fields.</b> Both
 * are supplied by the controller from the validated token, so no call site can confuse
 * token-sourced identity with client-supplied data — the enforceable form of ADR-016's trade-off,
 * and the reason {@code AnalyseGapRequest} carries no {@code authSubject}.
 *
 * <p>{@code OptionalInt} and {@code Optional} are unwrapped to a nullable field and a nullable
 * object here rather than serialised as wrappers. Jackson renders an {@code Optional} as an object
 * or a bare value depending on configuration, which is exactly the ambiguity an absent attainment
 * must not have; a field that is simply not there is unambiguous under
 * {@code default-property-inclusion: non_null}.
 */
@Component
class GapAnalysisWebMapper {

    AnalyseGapCommand toCommand(AnalyseGapRequest request, String authSubject,
                                boolean callerMayAnalyseAnyLearner) {
        return new AnalyseGapCommand(
                authSubject,
                new LearnerId(request.learnerId()),
                new CompetencyFrameworkId(request.frameworkId()),
                request.targetLevelCodes() == null ? Map.of() : request.targetLevelCodes(),
                callerMayAnalyseAnyLearner);
    }

    GapReportResponse toResponse(GapReport report) {
        return new GapReportResponse(
                report.id().value(),
                report.learnerId().value(),
                report.frameworkId().value(),
                report.generatedAt(),
                report.gaps().stream().map(this::toGap).toList());
    }

    GapReportSummaryResponse toSummary(GapReportSummary summary) {
        return new GapReportSummaryResponse(
                summary.id().value(),
                summary.frameworkId().value(),
                summary.generatedAt(),
                summary.gapCount());
    }

    private GapReportResponse.GapResponse toGap(SkillGap gap) {
        return new GapReportResponse.GapResponse(
                gap.id().value(),
                gap.target().competencyId().value(),
                gap.target().competencyCode(),
                gap.target().competencyName(),
                gap.target().targetLevelCode(),
                gap.target().targetOrdinal(),
                gap.severity().name(),
                gap.isUnassessed(),
                gap.shortfall().isPresent() ? gap.shortfall().getAsInt() : null,
                gap.attainment().map(this::toAttainment).orElse(null));
    }

    private GapReportResponse.AttainmentResponse toAttainment(AttainmentSnapshot attainment) {
        return new GapReportResponse.AttainmentResponse(
                attainment.attainedOrdinal(),
                attainment.attainedLevelCode(),
                attainment.resolvedAt(),
                attainment.evidence().stream().map(this::toEvidence).toList());
    }

    private GapReportResponse.EvidenceResponse toEvidence(EvidenceSnapshot evidence) {
        return new GapReportResponse.EvidenceResponse(
                evidence.type(),
                evidence.claimedOrdinal(),
                evidence.claimedLevelCode(),
                evidence.confidence(),
                evidence.source(),
                evidence.recordedAt());
    }
}
