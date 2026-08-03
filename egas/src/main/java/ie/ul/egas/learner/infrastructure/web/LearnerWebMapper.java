package ie.ul.egas.learner.infrastructure.web;

import ie.ul.egas.learner.application.CreateLearnerProfileCommand;
import ie.ul.egas.learner.application.RecordEvidenceCommand;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.LearnerProfileSummary;
import ie.ul.egas.learner.domain.model.ProficiencyAssertion;
import ie.ul.egas.learner.infrastructure.web.dto.CreateLearnerProfileRequest;
import ie.ul.egas.learner.infrastructure.web.dto.LearnerProfileResponse;
import ie.ul.egas.learner.infrastructure.web.dto.LearnerProfileSummaryResponse;
import ie.ul.egas.learner.infrastructure.web.dto.RecordEvidenceRequest;
import org.springframework.stereotype.Component;

/**
 * Hand-written web mapping, matching {@code FrameworkWebMapper}'s ruling that an annotation
 * processor buys little at this size.
 *
 * <p><b>The subject is a separate parameter, never a request field.</b> Both {@code toCommand}
 * overloads take {@code authSubject} explicitly, supplied by the controller from the validated
 * token. Keeping it out of the request record and out of the mapper's body-derived arguments means
 * no call site can confuse token-sourced identity with client-supplied data — the enforceable form
 * of the trade-off ADR-016 records.
 */
@Component
class LearnerWebMapper {

    CreateLearnerProfileCommand toCommand(CreateLearnerProfileRequest request, String authSubject) {
        return new CreateLearnerProfileCommand(authSubject, request.displayName());
    }

    RecordEvidenceCommand toCommand(RecordEvidenceRequest request, String authSubject) {
        return new RecordEvidenceCommand(
                authSubject,
                request.competencyId(),
                request.competencyFrameworkId(),
                request.type(),
                request.claimedOrdinal(),
                request.claimedLevelCode(),
                request.confidence(),
                request.source());
    }

    LearnerProfileResponse toResponse(LearnerProfile profile) {
        return new LearnerProfileResponse(
                profile.id().value(),
                profile.displayName().value(),
                profile.createdAt(),
                profile.assertions().stream().map(this::toAssertion).toList());
    }

    LearnerProfileSummaryResponse toSummary(LearnerProfileSummary summary) {
        return new LearnerProfileSummaryResponse(
                summary.id().value(),
                summary.displayName().value(),
                summary.assertionCount(),
                summary.createdAt());
    }

    private LearnerProfileResponse.AssertionResponse toAssertion(ProficiencyAssertion assertion) {
        return new LearnerProfileResponse.AssertionResponse(
                assertion.competencyId().value(),
                assertion.frameworkId().value(),
                assertion.attainedLevel().ordinal(),
                assertion.attainedLevel().code(),
                assertion.resolvedAt(),
                assertion.evidence().stream().map(this::toEvidence).toList());
    }

    private LearnerProfileResponse.EvidenceResponse toEvidence(EvidenceRecord record) {
        return new LearnerProfileResponse.EvidenceResponse(
                record.type(),
                record.claimedLevel().ordinal(),
                record.claimedLevel().code(),
                record.confidence().value(),
                record.source(),
                record.recordedAt());
    }
}
