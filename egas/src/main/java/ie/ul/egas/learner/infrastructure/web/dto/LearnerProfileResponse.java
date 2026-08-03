package ie.ul.egas.learner.infrastructure.web.dto;

import ie.ul.egas.learner.domain.model.EvidenceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full profile representation: metadata plus every assertion and the evidence supporting it.
 *
 * <p>Evidence is included rather than summarised because it is what makes a downstream skill gap
 * <em>explainable</em> instead of merely reported — the property RQ3's claim rests on. A response
 * that returned only the resolved level would discard the audit trail the aggregate maintains at
 * some cost.
 *
 * <p>The authenticated subject is <b>not</b> exposed. It is an authentication-layer value with no
 * meaning to a client, and echoing it would widen the profile's personal-data surface beyond the
 * display name for no benefit.
 */
public record LearnerProfileResponse(
        UUID id,
        String displayName,
        Instant createdAt,
        List<AssertionResponse> assertions) {

    public record AssertionResponse(
            UUID competencyId,
            UUID competencyFrameworkId,
            int attainedOrdinal,
            String attainedLevelCode,
            Instant resolvedAt,
            List<EvidenceResponse> evidence) {
    }

    public record EvidenceResponse(
            EvidenceType type,
            int claimedOrdinal,
            String claimedLevelCode,
            double confidence,
            String source,
            Instant recordedAt) {
    }
}
