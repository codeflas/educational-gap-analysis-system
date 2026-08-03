package ie.ul.egas.platform.infrastructure.web;

import ie.ul.egas.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the token endpoint against the real stack, including the RS256 token it hands
 * back — decoded here by the application's own {@link JwtDecoder} bean, so the test proves the
 * whole chain (controller → TokenService → encoder → decoder) agrees, not merely that a string
 * came back.
 *
 * <p>Requests carry {@code .with(user(...))} because the deny-by-default chain from Step 1 is
 * still in force and this phase deliberately leaves {@code SecurityConfig} untouched; the
 * endpoint itself ignores that authentication. Proving the endpoint is reachable
 * <em>unauthenticated</em> requires the {@code permitAll} rule that arrives with the
 * SecurityConfig rewrite, and that assertion belongs to the phase which introduces it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthTokenApiTests {

    private static final String CREDENTIALS = """
            {"username":"dev-educator","password":"dev-educator-password"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void issuesABearerTokenForValidCredentials() throws Exception {
        mvc.perform(post("/auth/token")
                        .with(user("anyone"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    void theIssuedTokenIsAnRs256TokenCarryingThePrincipalAndRoles() throws Exception {
        String body = mvc.perform(post("/auth/token")
                        .with(user("anyone"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Jwt decoded = jwtDecoder.decode(accessToken(body));
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
        assertThat(decoded.getSubject()).isEqualTo("dev-educator");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("egas");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("EDUCATOR");
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }

    @Test
    void rejectsAWrongPasswordWithUnauthorized() throws Exception {
        mvc.perform(post("/auth/token")
                        .with(user("anyone"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dev-educator","password":"not-the-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void answersAnUnknownUserAndAWrongPasswordIdentically() throws Exception {
        String wrongPassword = failedLogin("dev-educator", "not-the-password");
        String unknownUser = failedLogin("no-such-user", "dev-educator-password");

        assertThat(unknownUser)
                .as("anti-enumeration: the response must not reveal whether the username exists")
                .isEqualTo(wrongPassword);
    }

    @Test
    void rejectsAMalformedPayloadWithBadRequest() throws Exception {
        mvc.perform(post("/auth/token")
                        .with(user("anyone"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dev-educator"}"""))
                .andExpect(status().isBadRequest());
    }

    private String failedLogin(String username, String password) throws Exception {
        return mvc.perform(post("/auth/token")
                        .with(user("anyone"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    private String accessToken(String responseBody) {
        int start = responseBody.indexOf("\"access_token\":\"") + "\"access_token\":\"".length();
        return responseBody.substring(start, responseBody.indexOf('"', start));
    }
}
