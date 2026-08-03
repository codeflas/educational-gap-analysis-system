package ie.ul.egas.platform.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Wires the resolved {@link JwtKeyMaterial} into Nimbus encoder/decoder beans (ADR-013).
 *
 * <p><b>Profile boundary lives here.</b> {@link JwtKeyMaterial} is deliberately
 * Environment-free; this configuration is the one place that translates "is the dev profile
 * active" into the {@code devFallbackPermitted} flag, so the amendment-A5 policy — generated
 * keys only under dev, fail startup anywhere else — is enforced at context refresh, not at
 * first use. A misconfigured instance dies at boot with an actionable message.
 *
 * <p><b>RS256 pinned at both ends.</b> The encoder's sole JWK is our RSA signing key marked
 * RS256; the decoder is built from the paired public key with the algorithm fixed to RS256 —
 * no remote JWKS, no algorithm negotiation, no {@code none}. Token contents (claims, TTL) are
 * the {@code TokenService}'s concern in the next phase; this class owns only key material and
 * signature mechanics.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfiguration {

    private static final String DEV_PROFILE = "dev";

    @Bean
    JwtKeyMaterial jwtKeyMaterial(JwtProperties properties, Environment environment) {
        boolean devFallbackPermitted = environment.acceptsProfiles(Profiles.of(DEV_PROFILE));
        return JwtKeyMaterial.resolve(properties, devFallbackPermitted);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyMaterial keyMaterial) {
        RSAKey signingKey = new RSAKey.Builder(keyMaterial.publicKey())
                .privateKey(keyMaterial.privateKey())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /**
     * Validates signature, timestamps, and issuer. The default validator set that
     * {@code withPublicKey} installs does not inspect {@code iss}, so it is replaced here: a token
     * bearing a foreign issuer but signed with this instance's key would otherwise be accepted,
     * and the issuer is the only claim distinguishing "minted by us" from "minted by something
     * else holding this key". Realises the extension point recorded when this bean was introduced.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtKeyMaterial keyMaterial, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
