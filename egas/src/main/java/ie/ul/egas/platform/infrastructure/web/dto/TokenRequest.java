package ie.ul.egas.platform.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials submitted to the token endpoint. Bean Validation covers transport shape only
 * (tier 1 → HTTP 400); whether the credentials are <em>correct</em> is decided by
 * {@code TokenService}, and its answer is deliberately uniform (ADR-013 anti-enumeration).
 *
 * <p>The size bounds are a cheap guard against unbounded input reaching BCrypt, not a password
 * policy: these are development principals, and the hash length is fixed regardless.
 */
public record TokenRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String password) {

    /**
     * Overridden so a password can never reach a log, a stack trace, or a validation message
     * through the record's generated {@code toString}. The one place this type is most likely to
     * be printed is exactly the place a plaintext password must never appear.
     */
    @Override
    public String toString() {
        return "TokenRequest[username=" + username + ", password=***]";
    }
}
