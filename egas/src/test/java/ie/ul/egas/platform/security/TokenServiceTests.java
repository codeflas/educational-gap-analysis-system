package ie.ul.egas.platform.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import ie.ul.egas.platform.security.PrincipalProperties.DevelopmentPrincipal;
import ie.ul.egas.platform.security.TokenService.IssuedToken;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Credential verification and claim minting (ADR-013). Assembled from real collaborators — the
 * committed RSA fixtures, a real BCrypt encoder, a fixed {@link Clock} — rather than mocks, so
 * what these tests prove about claims and signatures is what the running system does.
 *
 * <p>The verifying decoder is bound to the <em>same</em> fixed clock as the service, so expiry
 * assertions are deterministic instead of racing wall-clock time.
 */
class TokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofHours(1);
    private static final String PASSWORD = "dev-password";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtKeyMaterial keyMaterial = JwtKeyMaterial.resolve(
            new JwtProperties("egas", TTL,
                    new ClassPathResource("jwt/test-private.pem"),
                    new ClassPathResource("jwt/test-public.pem")),
            false);

    @Test
    void issuesATokenCarryingTheConfiguredClaims() {
        IssuedToken issued = service(TTL).issue("dev-educator", PASSWORD);

        Jwt decoded = decoder().decode(issued.tokenValue());
        // Read as a raw string claim: "egas" is deliberately not a URL, and getIssuer() coerces.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("egas");
        assertThat(decoded.getSubject()).isEqualTo("dev-educator");
        assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("EDUCATOR");
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    void omitsAudienceAndJwtIdAsRecordedInAdr013() {
        IssuedToken issued = service(TTL).issue("dev-educator", PASSWORD);

        Jwt decoded = decoder().decode(issued.tokenValue());
        assertThat(decoded.getClaims()).doesNotContainKeys("aud", "jti");
    }

    @Test
    void derivesExpiryFromTheInjectedClockAndConfiguredTtl() {
        Duration shortTtl = Duration.ofMinutes(15);

        IssuedToken issued = service(shortTtl).issue("dev-educator", PASSWORD);

        assertThat(issued.issuedAt()).isEqualTo(NOW);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(shortTtl));
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
        assertThat(decoder().decode(issued.tokenValue()).getExpiresAt()).isEqualTo(NOW.plus(shortTtl));
    }

    @Test
    void carriesEveryConfiguredRoleDeterministically() {
        TokenService service = serviceFor(
                principal("dev-multi", Set.of(Role.LEARNER, Role.ADMIN, Role.EDUCATOR)),
                TTL, passwordEncoder);

        Jwt decoded = decoder().decode(service.issue("dev-multi", PASSWORD).tokenValue());

        assertThat(decoded.getClaimAsStringList("roles"))
                .containsExactly("ADMIN", "EDUCATOR", "LEARNER");
    }

    @Test
    void rejectsATamperedPayload() {
        String token = service(TTL).issue("dev-educator", PASSWORD).tokenValue();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 4) + "AAAA."
                + parts[2];

        JwtDecoder decoder = decoder();
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnUnknownUserIdenticallyToAWrongPassword() {
        TokenService service = service(TTL);

        Throwable wrongPassword = catchThrowable(() -> service.issue("dev-educator", "not-the-password"));
        Throwable unknownUser = catchThrowable(() -> service.issue("no-such-user", PASSWORD));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownUser)
                .as("anti-enumeration: the two failures must be indistinguishable")
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
    }

    @Test
    void verifiesAPasswordEvenWhenTheUsernameIsUnknown() {
        CountingPasswordEncoder counting = new CountingPasswordEncoder(passwordEncoder);
        TokenService service = serviceFor(
                principal("dev-educator", Set.of(Role.EDUCATOR)), TTL, counting);

        assertThatThrownBy(() -> service.issue("no-such-user", PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(counting.matchCount())
                .as("BCrypt must run on the unknown-user path too, or timing reveals which "
                        + "usernames exist")
                .isEqualTo(1);
    }

    @Test
    void propertyGuardsRejectMalformedPrincipalConfiguration() {
        assertThatThrownBy(() -> new DevelopmentPrincipal("  ", hash(), Set.of(Role.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");

        assertThatThrownBy(() -> new DevelopmentPrincipal("u", "plaintext", Set.of(Role.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BCrypt");

        assertThatThrownBy(() -> new DevelopmentPrincipal("u", hash(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roles");

        assertThatThrownBy(() -> new PrincipalProperties(List.of(
                principal("duplicate", Set.of(Role.ADMIN)),
                principal("duplicate", Set.of(Role.LEARNER)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void anEmptyRosterIsValidAndAuthenticatesNobody() {
        TokenService service = new TokenService(encoder(), jwtProperties(TTL),
                new PrincipalProperties(null), passwordEncoder, FIXED_CLOCK);

        assertThat(new PrincipalProperties(null).principals()).isEmpty();
        assertThatThrownBy(() -> service.issue("dev-educator", PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    private TokenService service(Duration ttl) {
        return serviceFor(principal("dev-educator", Set.of(Role.EDUCATOR)), ttl, passwordEncoder);
    }

    private TokenService serviceFor(DevelopmentPrincipal principal, Duration ttl,
                                    PasswordEncoder credentialEncoder) {
        return new TokenService(encoder(), jwtProperties(ttl),
                new PrincipalProperties(List.of(principal)), credentialEncoder, FIXED_CLOCK);
    }

    private DevelopmentPrincipal principal(String username, Set<Role> roles) {
        return new DevelopmentPrincipal(username, hash(), roles);
    }

    private String hash() {
        return passwordEncoder.encode(PASSWORD);
    }

    private JwtProperties jwtProperties(Duration ttl) {
        return new JwtProperties("egas", ttl,
                new ClassPathResource("jwt/test-private.pem"),
                new ClassPathResource("jwt/test-public.pem"));
    }

    private NimbusJwtEncoder encoder() {
        RSAKey signingKey = new RSAKey.Builder(keyMaterial.publicKey())
                .privateKey(keyMaterial.privateKey())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    private JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(FIXED_CLOCK);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }

    /** Hand-rolled counting decorator — the codebase uses real collaborators, not mocks. */
    private static final class CountingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate;
        private int matchCount;

        private CountingPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchCount++;
            return delegate.matches(rawPassword, encodedPassword);
        }

        private int matchCount() {
            return matchCount;
        }
    }
}
