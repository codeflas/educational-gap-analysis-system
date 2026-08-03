package ie.ul.egas.learner.infrastructure.web.dto;

import ie.ul.egas.learner.domain.model.EvidenceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Evidence payload for the caller's own profile.
 *
 * <p>Two fields are absent by design. There is no {@code authSubject}: the profile written to is
 * the caller's, resolved from the token (ADR-016). And there is no timestamp: {@code recordedAt} is
 * stamped by the service from the injected {@code Clock}, because a caller-supplied value is
 * unverifiable and would let a caller post-date evidence to win the recency tie-break in the
 * resolution policy (ADR-018 Amendment 1).
 *
 * <p>Validation here is transport-tier only. The bounds duplicate {@code Confidence} and
 * {@code AttainedLevel} deliberately, so a malformed request fails as a 400 at the edge rather than
 * reaching the domain and surfacing as a 400 from a value-object exception — same outcome, better
 * message, and the value objects remain the authority either way.
 */
public record RecordEvidenceRequest(
        @NotNull UUID competencyId,
        @NotNull UUID competencyFrameworkId,
        @NotNull EvidenceType type,
        @PositiveOrZero int claimedOrdinal,
        @NotBlank @Size(max = 50) String claimedLevelCode,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        @NotBlank @Size(max = 500) String source) {
}
