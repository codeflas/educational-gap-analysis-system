package ie.ul.egas.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Operational contract for JWT issuance and validation (ADR-013).
 *
 * <p>Key material is supplied as locations of PEM files — PKCS#8 for the private key
 * ({@code BEGIN PRIVATE KEY}) and X.509 SubjectPublicKeyInfo for the public key
 * ({@code BEGIN PUBLIC KEY}) — environment-injected in any real deployment. Both locations are
 * optional <em>as properties</em> because the dev profile may run without them; the fail-fast
 * policy for their absence outside dev (amendment A5) is enforced by {@link JwtKeyMaterial},
 * not by nullability annotations here, since the rule is profile-dependent.
 *
 * <p>Registered with the Spring context in the encoder/decoder configuration phase; until then
 * this record is inert.
 */
@ConfigurationProperties(prefix = "egas.security.jwt")
public record JwtProperties(
        @DefaultValue("egas") String issuer,
        @DefaultValue("PT1H") Duration ttl,
        Resource privateKeyLocation,
        Resource publicKeyLocation) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("egas.security.jwt.issuer must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("egas.security.jwt.ttl must be a positive duration");
        }
    }

    /** True when both halves of the signing key pair are configured. */
    public boolean hasConfiguredKeyPair() {
        return privateKeyLocation != null && publicKeyLocation != null;
    }
}
