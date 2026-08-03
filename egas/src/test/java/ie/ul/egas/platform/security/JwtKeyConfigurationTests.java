package ie.ul.egas.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-level enforcement of the ADR-013 / amendment-A5 key policy — the counterpart
 * promised by {@code JwtKeyMaterialTests}: a non-dev Spring context without valid key material
 * must fail <em>startup</em>, not first use. Runs on {@link ApplicationContextRunner} scoped
 * to {@link JwtConfiguration} alone: startup-failure semantics need no web server, database,
 * or Testcontainers, and the failure cases would be prohibitively slow as full-context tests.
 *
 * <p>The runner does not load {@code application.properties}, so these contexts see exactly
 * the properties each test supplies — including the absence of the test keypair that the
 * integration-test classpath configures.
 */
class JwtKeyConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfiguration.class);

    @Test
    void nonDevContextWithoutKeyMaterialFailsStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Refusing to start")
                    .hasStackTraceContaining("egas.security.jwt.private-key-location");
        });
    }

    @Test
    void nonDevContextWithUnparseableKeyMaterialFailsStartup() {
        contextRunner
                .withPropertyValues(
                        "egas.security.jwt.private-key-location=classpath:jwt/invalid-key.pem",
                        "egas.security.jwt.public-key-location=classpath:jwt/test-public.pem")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("PKCS#8");
                });
    }

    @Test
    void devProfileWithoutKeysBootsOnTheGeneratedFallback() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtKeyMaterial.class).generated()).isTrue();
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }

    @Test
    void configuredPairWiresAWorkingEncoderDecoderRoundTrip() {
        contextRunner
                .withPropertyValues(
                        "egas.security.jwt.private-key-location=classpath:jwt/test-private.pem",
                        "egas.security.jwt.public-key-location=classpath:jwt/test-public.pem")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtKeyMaterial.class).generated()).isFalse();

                    Instant now = Instant.now();
                    JwtClaimsSet claims = JwtClaimsSet.builder()
                            .issuer("egas")
                            .subject("wiring-check")
                            .issuedAt(now)
                            .expiresAt(now.plusSeconds(300))
                            .build();
                    String token = context.getBean(JwtEncoder.class)
                            .encode(JwtEncoderParameters.from(
                                    JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                            .getTokenValue();

                    Jwt decoded = context.getBean(JwtDecoder.class).decode(token);
                    assertThat(decoded.getSubject()).isEqualTo("wiring-check");
                    assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
                });
    }
}
