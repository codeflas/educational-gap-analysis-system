package ie.ul.egas.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

/**
 * Resolved RSA signing material for JWT issuance and validation, embodying the ADR-013 /
 * amendment-A5 key policy:
 *
 * <ol>
 *   <li><b>Both locations configured</b> — parse (PKCS#8 private via
 *       {@link RsaKeyConverters#pkcs8()}, X.509 SPKI public via {@link RsaKeyConverters#x509()});
 *       any parse failure aborts startup naming the offending property and expected format.
 *       A modulus comparison additionally rejects a public key that is not the pair of the
 *       private key — a mismatched pair would mint tokens that fail the instance's own
 *       validation, a misconfiguration far better surfaced at boot than at first login.</li>
 *   <li><b>Exactly one location configured</b> — rejected outright, in every profile:
 *       half-configuration is always an operator error.</li>
 *   <li><b>Neither configured, dev fallback permitted</b> — generate an in-memory RSA-2048
 *       pair and log it loudly; tokens do not survive a restart. The permission flag is
 *       supplied by the wiring layer from the active profiles; this class stays
 *       Environment-free for direct unit testing.</li>
 *   <li><b>Neither configured, fallback not permitted</b> — refuse to start. Never silent
 *       self-signing outside dev.</li>
 * </ol>
 *
 * <p>Verified end-to-end by the Step 3 key-handling spike (openssl-generated PEM → JDK parse →
 * SHA256withRSA round-trip) before this class was written.
 */
final class JwtKeyMaterial {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyMaterial.class);
    private static final int GENERATED_KEY_SIZE = 2048;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final boolean generated;

    private JwtKeyMaterial(RSAPublicKey publicKey, RSAPrivateKey privateKey, boolean generated) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        this.generated = generated;
    }

    /**
     * Applies the key policy. {@code devFallbackPermitted} must be true only when the dev
     * profile is active; the caller (the JWT configuration, next phase) derives it from the
     * Spring {@code Environment}.
     */
    static JwtKeyMaterial resolve(JwtProperties properties, boolean devFallbackPermitted) {
        Objects.requireNonNull(properties, "properties must not be null");

        if (properties.hasConfiguredKeyPair()) {
            RSAPrivateKey privateKey = readPrivateKey(properties.privateKeyLocation());
            RSAPublicKey publicKey = readPublicKey(properties.publicKeyLocation());
            if (!publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new JwtKeyConfigurationException(
                        "Configured JWT keys are not a pair: the public key's modulus does not match the "
                                + "private key's. Check egas.security.jwt.private-key-location and "
                                + "egas.security.jwt.public-key-location.");
            }
            log.info("JWT signing keys loaded from configuration (RSA, {} bits).",
                    publicKey.getModulus().bitLength());
            return new JwtKeyMaterial(publicKey, privateKey, false);
        }

        if (properties.privateKeyLocation() != null || properties.publicKeyLocation() != null) {
            throw new JwtKeyConfigurationException(
                    "JWT key configuration is incomplete: egas.security.jwt.private-key-location and "
                            + "egas.security.jwt.public-key-location must be set together.");
        }

        if (devFallbackPermitted) {
            KeyPair pair = generatePair();
            log.warn("****************************************************************************");
            log.warn("DEV-ONLY JWT KEYS: no key material configured; generated an in-memory");
            log.warn("RSA-{} pair. Tokens will NOT survive a restart. Any non-dev profile", GENERATED_KEY_SIZE);
            log.warn("started like this fails by design (ADR-013, amendment A5).");
            log.warn("****************************************************************************");
            return new JwtKeyMaterial(
                    (RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), true);
        }

        throw new JwtKeyConfigurationException(
                "No JWT key material configured and the dev profile is not active. Refusing to start "
                        + "rather than silently self-sign (ADR-013, amendment A5). Set "
                        + "egas.security.jwt.private-key-location and egas.security.jwt.public-key-location, "
                        + "or activate the 'dev' profile for local development.");
    }

    RSAPublicKey publicKey() {
        return publicKey;
    }

    RSAPrivateKey privateKey() {
        return privateKey;
    }

    /** True when the material was generated in-memory under the dev fallback. */
    boolean generated() {
        return generated;
    }

    private static RSAPrivateKey readPrivateKey(Resource location) {
        try (InputStream in = location.getInputStream()) {
            RSAPrivateKey key = RsaKeyConverters.pkcs8().convert(in);
            if (key == null) {
                throw new IllegalArgumentException("converter produced no key");
            }
            return key;
        } catch (IOException | RuntimeException e) {
            throw new JwtKeyConfigurationException(
                    "Failed to read or parse the JWT private key from " + location.getDescription()
                            + " — expected a PKCS#8 PEM ('BEGIN PRIVATE KEY').", e);
        }
    }

    private static RSAPublicKey readPublicKey(Resource location) {
        try (InputStream in = location.getInputStream()) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(in);
            if (key == null) {
                throw new IllegalArgumentException("converter produced no key");
            }
            return key;
        } catch (IOException | RuntimeException e) {
            throw new JwtKeyConfigurationException(
                    "Failed to read or parse the JWT public key from " + location.getDescription()
                            + " — expected an X.509 SubjectPublicKeyInfo PEM ('BEGIN PUBLIC KEY').", e);
        }
    }

    private static KeyPair generatePair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new JwtKeyConfigurationException("RSA key generation is unavailable on this JVM.", e);
        }
    }
}
