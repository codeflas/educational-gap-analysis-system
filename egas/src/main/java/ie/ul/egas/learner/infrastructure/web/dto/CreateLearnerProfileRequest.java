package ie.ul.egas.learner.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Provisioning payload.
 *
 * <p><b>There is deliberately no {@code authSubject} field.</b> The subject comes from the
 * validated token and is passed to the mapper as a separate argument (ADR-016); offering it here
 * would let a caller provision a profile for someone else. Jackson ignores unknown properties by
 * Boot's default, so a request carrying one is silently disregarded rather than rejected — which
 * is the desired behaviour and is asserted by test rather than assumed.
 *
 * <p>Bean Validation covers transport shape only (tier 1 → HTTP 400); the bounds mirror
 * {@code DisplayName}, which remains the authority.
 */
public record CreateLearnerProfileRequest(
        @NotBlank @Size(max = 200) String displayName) {
}
