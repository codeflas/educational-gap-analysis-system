package ie.ul.egas.platform;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.FrameworkFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorisation matrix and failure semantics of the single filter chain (ADR-010, ADR-015),
 * exercised with <em>real</em> RS256 tokens rather than {@code jwt()} post-processors: the point
 * of these tests is precisely the machinery a post-processor would bypass — the decoder, the
 * issuer and timestamp validators, and the converter that turns the {@code roles} claim into
 * {@code ROLE_*} authorities.
 *
 * <p>401 versus 403 is asserted per failure mode. Conflating them is the classic resource-server
 * defect: an absent, malformed, expired, or foreign-issuer token is an <em>authentication</em>
 * failure, whereas a perfectly valid token lacking a role is an <em>authorisation</em> failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityAuthorizationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void deniesAnAbsentTokenAndAdvertisesTheBearerScheme() throws Exception {
        mvc.perform(get("/api/frameworks"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void rejectsAMalformedTokenAsAnInvalidToken() throws Exception {
        mvc.perform(get("/api/frameworks").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        Instant longAgo = Instant.now().minusSeconds(7200);
        String expired = token(claims -> claims
                .issuer("egas")
                .subject("dev-educator")
                .issuedAt(longAgo)
                .expiresAt(longAgo.plusSeconds(60))
                .claim("roles", List.of("EDUCATOR")));

        mvc.perform(get("/api/frameworks").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    void rejectsATokenMintedByAnotherIssuerEvenWhenSignedWithOurKey() throws Exception {
        Instant now = Instant.now();
        String foreign = token(claims -> claims
                .issuer("not-egas")
                .subject("dev-educator")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("EDUCATOR")));

        mvc.perform(get("/api/frameworks").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEndpointIsReachableWithoutAuthentication() throws Exception {
        mvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dev-educator","password":"dev-educator-password"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    void aLearnerMayReadTheRegistry() throws Exception {
        mvc.perform(get("/api/frameworks")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dev-learner", "dev-learner-password")))
                .andExpect(status().isOk());
    }

    @Test
    void aLearnerMayNotRegisterAndIsForbiddenRatherThanUnauthorised() throws Exception {
        mvc.perform(post("/api/frameworks")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dev-learner", "dev-learner-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(framework("Security Learner Denied Framework")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEducatorMayRegister() throws Exception {
        mvc.perform(post("/api/frameworks")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dev-educator", "dev-educator-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(framework("Security Educator Framework")))
                .andExpect(status().isCreated());
    }

    @Test
    void anAdminMayRegister() throws Exception {
        mvc.perform(post("/api/frameworks")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dev-admin", "dev-admin-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(framework("Security Admin Framework")))
                .andExpect(status().isCreated());
    }

    @Test
    void completesAFullIssueThenRegisterThenReadCycle() throws Exception {
        String authorization = bearer("dev-educator", "dev-educator-password");

        String created = mvc.perform(post("/api/frameworks")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(framework("Security End To End Framework")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();
        mvc.perform(get("/api/frameworks/{id}", id).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Security End To End Framework"));
    }

    /** Obtains a token the way a client does — through the issuance endpoint. */
    private String bearer(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("access_token").asText();
    }

    private String token(java.util.function.Consumer<JwtClaimsSet.Builder> customiser) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        customiser.accept(claims);
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    private String framework(String name) throws Exception {
        return objectMapper.writeValueAsString(FrameworkFixtures.validRequest(name, "1.0"));
    }
}
