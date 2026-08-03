package ie.ul.egas.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Factory-level verification of the ADR-013 / amendment-A5 key policy — every branch:
 * configured pair (parse + pair-consistency + usable for SHA256withRSA), dev fallback,
 * fail-fast outside dev, partial configuration, unparseable PEM, mismatched pair, and
 * property guards. Context-level enforcement (a non-dev Spring context failing startup)
 * is covered by JwtKeyConfigurationTests in the wiring phase, per the plan's unit budget.
 *
 * <p>PEM fixtures under src/test/resources/jwt/ are real openssl output (see the fixtures
 * README), so these tests exercise the exact formats production key provisioning produces.
 */
class JwtKeyMaterialTests {

    private static final byte[] PAYLOAD = "egas-key-spike".getBytes(StandardCharsets.UTF_8);

    @Test
    void loadsConfiguredPemPairAndSignsVerifiably() throws Exception {
        JwtKeyMaterial material = JwtKeyMaterial.resolve(
                properties(pem("jwt/test-private.pem"), pem("jwt/test-public.pem")), false);

        assertThat(material.generated()).isFalse();
        assertThat(material.publicKey().getModulus()).isEqualTo(material.privateKey().getModulus());
        assertThat(material.publicKey().getModulus().bitLength()).isEqualTo(2048);
        assertThat(signAndVerify(material)).isTrue();
    }

    @Test
    void generatesDevFallbackWhenPermittedAndUnconfigured() throws Exception {
        JwtKeyMaterial material = JwtKeyMaterial.resolve(properties(null, null), true);

        assertThat(material.generated()).isTrue();
        assertThat(material.publicKey().getModulus().bitLength()).isEqualTo(2048);
        assertThat(signAndVerify(material)).isTrue();
    }

    @Test
    void refusesToResolveWithoutKeysOutsideDev() {
        assertThatThrownBy(() -> JwtKeyMaterial.resolve(properties(null, null), false))
                .isInstanceOf(JwtKeyConfigurationException.class)
                .hasMessageContaining("dev")
                .hasMessageContaining("egas.security.jwt.private-key-location");
    }

    @Test
    void rejectsPartialConfigurationEvenWhenDevFallbackIsPermitted() {
        assertThatThrownBy(() -> JwtKeyMaterial.resolve(
                properties(pem("jwt/test-private.pem"), null), true))
                .isInstanceOf(JwtKeyConfigurationException.class)
                .hasMessageContaining("must be set together");
    }

    @Test
    void rejectsUnparseableKeyMaterialWithAnActionableMessage() {
        assertThatThrownBy(() -> JwtKeyMaterial.resolve(
                properties(pem("jwt/invalid-key.pem"), pem("jwt/test-public.pem")), false))
                .isInstanceOf(JwtKeyConfigurationException.class)
                .hasMessageContaining("PKCS#8");
    }

    @Test
    void rejectsAPublicKeyThatIsNotThePairOfThePrivateKey() {
        assertThatThrownBy(() -> JwtKeyMaterial.resolve(
                properties(pem("jwt/test-private.pem"), pem("jwt/other-public.pem")), false))
                .isInstanceOf(JwtKeyConfigurationException.class)
                .hasMessageContaining("not a pair");
    }

    @Test
    void propertyGuardsRejectBlankIssuerAndNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties("   ", Duration.ofHours(1), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> new JwtProperties("egas", Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }

    private JwtProperties properties(Resource privateKeyLocation, Resource publicKeyLocation) {
        return new JwtProperties("egas", Duration.ofHours(1), privateKeyLocation, publicKeyLocation);
    }

    private Resource pem(String classpathLocation) {
        return new ClassPathResource(classpathLocation);
    }

    private boolean signAndVerify(JwtKeyMaterial material) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(material.privateKey());
        signer.update(PAYLOAD);
        byte[] signatureBytes = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(material.publicKey());
        verifier.update(PAYLOAD);
        return verifier.verify(signatureBytes);
    }
}
