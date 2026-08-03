package ie.ul.egas.learner.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.learner.LearnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the learner web adapter against the real stack, exercised with <em>real</em>
 * minted tokens rather than {@code jwt()} post-processors.
 *
 * <p>The token choice is the point. Phase 3 proved the ownership rule with no security
 * infrastructure at all; what remains unproven is the wiring between them — the converter that
 * turns a {@code roles} claim into {@code ROLE_*} authorities, and the controller that collapses
 * those authorities into {@code callerMayReadAny}. A post-processor with hardcoded authorities
 * would bypass exactly that machinery, so nothing here uses one.
 *
 * <p>Profiles are unique per authenticated subject and only four test principals exist, so the
 * suite truncates the learner tables before each test rather than relying on unique naming. That
 * is the per-suite cleanup the plan's risk register prescribes for exactly this inherited problem;
 * without it the second provisioning in the class would collide with the first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LearnerProfileApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clearLearnerProfiles() {
        // Assertions and evidence cascade from the profile row (V200), so one delete suffices.
        jdbc.sql("delete from learner.profile").update();
    }

    // --- provisioning ---------------------------------------------------------------------------

    @Test
    void provisionsTheCallersOwnProfileWithCreatedAndLocation() throws Exception {
        mvc.perform(post("/api/learners/me")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada Lovelace"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.assertions").isEmpty())
                .andExpect(jsonPath("$.authSubject").doesNotExist());
    }

    @Test
    void ignoresAnAuthSubjectSuppliedInTheRequestBody() throws Exception {
        // The ADR-016 trade-off made testable: identity comes from the token, and a body field
        // claiming otherwise must have no effect. Anything else would let a caller provision a
        // profile for someone else.
        String token = tokenFor("test-admin", "test-admin-password");

        mvc.perform(post("/api/learners/me")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Impersonation Attempt","authSubject":"test-educator"}"""))
                .andExpect(status().isCreated());

        // The profile belongs to the token's subject, so the smuggled subject has no profile of
        // this name — proven by reading each caller's own profile back.
        mvc.perform(get("/api/learners/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Impersonation Attempt"));
    }

    @Test
    void refusesASecondProfileForTheSameCallerWithConflict() throws Exception {
        String token = tokenFor("test-educator", "test-educator-password");
        createProfile(token, "First Profile");

        mvc.perform(post("/api/learners/me")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Second Profile"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void anyAuthenticatedPrincipalMayProvisionTheirOwnProfile() throws Exception {
        // Plan amendment A2: provisioning is not role-restricted. ADR-015 Amendment 1's rule set
        // admits everything under /api/learners/** on authentication alone.
        mvc.perform(post("/api/learners/me")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Learner Provisioned"}"""))
                .andExpect(status().isCreated());
    }

    // --- evidence -------------------------------------------------------------------------------

    @Test
    void recordsEvidenceAndReturnsTheReResolvedProfile() throws Exception {
        String token = learnerToken();
        createProfile(token, "Evidence Owner");

        mvc.perform(post("/api/learners/me/evidence")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceBody(1, "L1", 0.4)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/learners/me/evidence")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceBody(3, "L3", 0.9)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assertions.length()").value(1))
                .andExpect(jsonPath("$.assertions[0].attainedLevelCode").value("L3"))
                .andExpect(jsonPath("$.assertions[0].evidence.length()").value(2))
                // Evidence is returned so a downstream gap can be explained, not merely reported.
                .andExpect(jsonPath("$.assertions[0].evidence[0].claimedLevelCode").value("L1"))
                .andExpect(jsonPath("$.assertions[0].evidence[0].recordedAt").isNotEmpty());
    }

    @Test
    void refusesEvidenceFromACallerWithNoProfile() throws Exception {
        mvc.perform(post("/api/learners/me/evidence")
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenFor("test-learner", "test-learner-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceBody(1, "L1", 0.5)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAMalformedEvidencePayloadWithBadRequest() throws Exception {
        mvc.perform(post("/api/learners/me/evidence")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"competencyId":null,"confidence":5.0}"""))
                .andExpect(status().isBadRequest());
    }

    // --- ownership matrix (ADR-015 Amendment 1) -------------------------------------------------

    @Test
    void theOwnerMayReadTheirOwnProfileByIdentifier() throws Exception {
        String token = learnerToken();
        String id = createProfile(token, "Self Reader");

        mvc.perform(get("/api/learners/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Self Reader"));
    }

    @Test
    void aNonOwnerLearnerReceivesNotFoundIndistinguishableFromAnUnknownIdentifier() throws Exception {
        // The anti-enumeration property at the HTTP boundary: 404, not 403, and a body that cannot
        // be used to tell "exists but forbidden" from "does not exist".
        String ownerToken = learnerToken();
        String id = createProfile(ownerToken, "Private Profile");
        // A genuinely different non-privileged subject. Using an EDUCATOR here would exercise the
        // privileged-reader path instead and prove nothing about ownership denial.
        String intruderToken = tokenFor("test-learner-2", "test-learner-2-password");

        UUID unknownId = UUID.randomUUID();

        JsonNode forbidden = problemDetail(get("/api/learners/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, intruderToken));
        JsonNode absent = problemDetail(get("/api/learners/{id}", unknownId)
                .header(HttpHeaders.AUTHORIZATION, intruderToken));

        // Every field that could betray existence must match exactly.
        for (String field : new String[] {"type", "title", "status", "detail"}) {
            assertThat(forbidden.get(field))
                    .as("field '%s' must not distinguish forbidden from absent", field)
                    .isEqualTo(absent.get(field));
        }

        // 'instance' does differ, and legitimately so: it echoes the URI the caller itself
        // requested, so it conveys nothing the caller did not already supply. Asserted explicitly
        // rather than ignored, so that a future change which put anything else there would fail.
        assertThat(forbidden.get("instance").asText()).isEqualTo("/api/learners/" + id);
        assertThat(absent.get("instance").asText()).isEqualTo("/api/learners/" + unknownId);
    }

    private JsonNode problemDetail(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mvc.perform(request)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void anEducatorMayReadAProfileTheyDoNotOwn() throws Exception {
        String id = createProfile(learnerToken(), "Educator Readable");

        mvc.perform(get("/api/learners/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenFor("test-educator", "test-educator-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Educator Readable"));
    }

    @Test
    void anAdminMayReadAProfileTheyDoNotOwn() throws Exception {
        String id = createProfile(learnerToken(), "Admin Readable");

        mvc.perform(get("/api/learners/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenFor("test-admin", "test-admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Admin Readable"));
    }

    // --- listing and chain rules ----------------------------------------------------------------

    @Test
    void anEducatorMayListSummariesWithoutEvidence() throws Exception {
        createProfile(learnerToken(), "Listed Profile");

        mvc.perform(get("/api/learners")
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenFor("test-educator", "test-educator-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assertionCount").exists())
                .andExpect(jsonPath("$[0].assertions").doesNotExist());
    }

    @Test
    void aLearnerMayNotListEveryProfileAndIsForbiddenRatherThanNotFound() throws Exception {
        // Insufficient *role* is 403 from the filter chain: it discloses nothing about any
        // particular profile, unlike the ownership case above.
        mvc.perform(get("/api/learners").header(HttpHeaders.AUTHORIZATION, learnerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesUnauthenticatedAccessWithTheBearerChallenge() throws Exception {
        mvc.perform(get("/api/learners/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    // --- helpers --------------------------------------------------------------------------------

    /** The primary LEARNER-role subject; the tables are cleared before each test. */
    private String learnerToken() throws Exception {
        return tokenFor("test-learner", "test-learner-password");
    }

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("access_token").asText();
    }

    private String createProfile(String token, String displayName) throws Exception {
        String body = mvc.perform(post("/api/learners/me")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String evidenceBody(int ordinal, String code, double confidence) {
        return """
                {"competencyId":"%s","competencyFrameworkId":"%s","type":"SELF_DECLARED",
                 "claimedOrdinal":%d,"claimedLevelCode":"%s","confidence":%s,"source":"api test"}"""
                .formatted(LearnerFixtures.SOFTWARE_DESIGN.value(),
                        LearnerFixtures.FRAMEWORK.value(), ordinal, code, confidence);
    }
}
