package ie.ul.egas.gapanalysis.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.competency.application.CompetencyFrameworkService;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
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

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the gap-analysis web adapter against the real stack, exercised with <em>real</em>
 * minted tokens rather than {@code jwt()} post-processors.
 *
 * <p>The token choice is the point, as it was for learner profiles. Phase 4b proved the ownership
 * matrix with no security infrastructure at all; what remains unproven is the wiring between them —
 * the converter that turns a {@code roles} claim into {@code ROLE_*} authorities, the controller
 * that collapses those authorities into a boolean, and the identity contract that resolves a token
 * subject to a learner. A post-processor with hardcoded authorities would bypass exactly that
 * machinery, so nothing here uses one.
 *
 * <p>The suite also proves the two denial shapes are actually distinguishable over HTTP, which is
 * where ADR-015 Amendment 2's reasoning either holds or does not: a report the caller may not read
 * must be indistinguishable from one that does not exist, while a learner-scoped operation must say
 * plainly that it was refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GapReportApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CompetencyFrameworkService frameworks;

    @Autowired
    CompetencyModelProjectionRepository projection;

    private CompetencyFrameworkId framework;

    @BeforeEach
    void resetLearnerStateAndRegisterFramework() {
        // Profiles are unique per authenticated subject and only four test principals exist, so the
        // learner tables are cleared before each test. Gap reports cascade from nothing outside
        // gap_analysis, so they are cleared directly.
        jdbc.sql("delete from gap_analysis.gap_report").update();
        jdbc.sql("delete from learner.profile").update();
        framework = registerFramework();
    }

    // --- analysis -------------------------------------------------------------------------------

    @Test
    void analysesTheCallersOwnGapsWithCreatedAndLocation() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Ada Lovelace");
        recordEvidence(token, "SE-DSN", 1, "L1", 0.9);

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.learnerId").value(learnerId))
                .andExpect(jsonPath("$.frameworkId").value(framework.value().toString()))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.gaps.length()").value(2));
    }

    @Test
    void theResponseCarriesTheWholeExplainabilityChain() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Explainable Learner");
        recordEvidence(token, "SE-DSN", 1, "L1", 0.9);

        // SE-DSN is described at L2, so the default target is L2 and a learner at L1 is one short.
        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].targetLevelCode")
                        .value("L2"))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].severity")
                        .value("MINOR"))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].shortfall").value(1))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].unassessed").value(false))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].attainment.attainedLevelCode")
                        .value("L1"))
                .andExpect(jsonPath(
                        "$.gaps[?(@.competencyCode=='SE-DSN')].attainment.evidence[0].confidence")
                        .value(0.9))
                .andExpect(jsonPath(
                        "$.gaps[?(@.competencyCode=='SE-DSN')].attainment.evidence[0].source")
                        .value("api test"));
    }

    @Test
    void anUnassessedFindingOmitsAttainmentRatherThanSendingZeros() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Partially Evidenced");
        recordEvidence(token, "SE-DSN", 1, "L1", 0.9);

        // SE-TST has no evidence at all: absence must reach the wire as absence (ADR-021).
        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-TST')].unassessed").value(true))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-TST')].severity")
                        .value("UNASSESSED"))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-TST')].attainment")
                        .doesNotExist())
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-TST')].shortfall")
                        .doesNotExist());
    }

    @Test
    void anExplicitTargetIsHonoured() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Targeted Learner");
        recordEvidence(token, "SE-DSN", 1, "L1", 0.9);

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, "{\"SE-DSN\":\"L3\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].targetLevelCode")
                        .value("L3"))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].shortfall").value(2))
                .andExpect(jsonPath("$.gaps[?(@.competencyCode=='SE-DSN')].severity")
                        .value("MODERATE"));
    }

    @Test
    void aTargetTheFrameworkDoesNotDefineIsRejectedAsBadRequest() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Bad Target");

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, "{\"SE-DSN\":\"L9\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown target level"));
    }

    @Test
    void aFrameworkWithNoProjectionIsUnprocessable() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "No Model");

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"learnerId":"%s","frameworkId":"%s"}"""
                                .formatted(learnerId, UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Competency model unavailable"));
    }

    @Test
    void aMalformedPayloadIsRejectedBeforeAnythingIsComputed() throws Exception {
        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"frameworkId":"%s"}""".formatted(framework.value())))
                .andExpect(status().isBadRequest());
    }

    // --- authentication -------------------------------------------------------------------------

    @Test
    void everyEndpointDemandsABearerToken() throws Exception {
        mvc.perform(post("/api/gap-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(UUID.randomUUID().toString(), null)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        mvc.perform(get("/api/gap-reports/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/gap-reports").param("learnerId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRejectedTokenNeverReachesTheApplicationLayer() throws Exception {
        mvc.perform(get("/api/gap-reports/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // --- ownership ------------------------------------------------------------------------------

    @Test
    void aLearnerMayNotAnalyseAnotherLearnerAndIsToldSo() throws Exception {
        // 403, not 404: the learner identifier came from the caller and was never looked up, so
        // refusing discloses nothing about it (ADR-015 Amendment 2).
        String otherLearnerId = createProfile(secondLearnerToken(), "Someone Else");

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(otherLearnerId, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not permitted for this learner"));
    }

    @Test
    void anEducatorMayAnalyseAnotherLearner() throws Exception {
        String learnerId = createProfile(learnerToken(), "Analysed By Educator");

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, educatorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.learnerId").value(learnerId));
    }

    @Test
    void aCallerWithNoProfileMayNotAnalyseAnyone() throws Exception {
        // A valid token does not imply enrolment (ADR-017): an unenrolled caller owns nothing.
        String learnerId = createProfile(learnerToken(), "Enrolled Learner");

        mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, secondLearnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aLearnerMayReadTheirOwnReport() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Own Report");
        String reportId = analyse(token, learnerId);

        mvc.perform(get("/api/gap-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.learnerId").value(learnerId));
    }

    @Test
    void anotherLearnersReportIsIndistinguishableFromOneThatDoesNotExist() throws Exception {
        // The assertion ADR-015 Amendment 2 turns on: status AND body must match, or the endpoint
        // becomes an oracle over report identifiers — and a report names the learner it is about.
        String ownerToken = learnerToken();
        String learnerId = createProfile(ownerToken, "Private Report");
        String reportId = analyse(ownerToken, learnerId);
        String intruderToken = secondLearnerToken();

        String forbidden = mvc.perform(get("/api/gap-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, intruderToken))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String absent = mvc.perform(get("/api/gap-reports/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, intruderToken))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // The RFC 9457 'instance' member legitimately differs — it names the request path — so the
        // comparison is over every other member.
        assertThat(withoutInstance(forbidden))
                .as("present-but-forbidden must be byte-identical to absent")
                .isEqualTo(withoutInstance(absent));
    }

    @Test
    void anEducatorMayReadAnotherLearnersReport() throws Exception {
        String ownerToken = learnerToken();
        String learnerId = createProfile(ownerToken, "Educator Readable");
        String reportId = analyse(ownerToken, learnerId);

        mvc.perform(get("/api/gap-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, educatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId));
    }

    @Test
    void anAdministratorMayReadAnotherLearnersReport() throws Exception {
        String ownerToken = learnerToken();
        String learnerId = createProfile(ownerToken, "Admin Readable");
        String reportId = analyse(ownerToken, learnerId);

        mvc.perform(get("/api/gap-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk());
    }

    // --- history --------------------------------------------------------------------------------

    @Test
    void aLearnerListsTheirOwnHistoryNewestFirstWithoutFindings() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Historic Learner");
        analyse(token, learnerId);
        analyse(token, learnerId);

        mvc.perform(get("/api/gap-reports").param("learnerId", learnerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].gapCount").value(2))
                .andExpect(jsonPath("$[0].frameworkId").value(framework.value().toString()))
                .andExpect(jsonPath("$[0].generatedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].gaps").doesNotExist());
    }

    @Test
    void aLearnerMayNotListAnotherLearnersHistory() throws Exception {
        String otherLearnerId = createProfile(secondLearnerToken(), "Private History");

        mvc.perform(get("/api/gap-reports").param("learnerId", otherLearnerId)
                        .header(HttpHeaders.AUTHORIZATION, learnerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEducatorMayListAnotherLearnersHistory() throws Exception {
        String token = learnerToken();
        String learnerId = createProfile(token, "Educator Visible History");
        analyse(token, learnerId);

        mvc.perform(get("/api/gap-reports").param("learnerId", learnerId)
                        .header(HttpHeaders.AUTHORIZATION, educatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listingRequiresTheLearnerItIsScopedTo() throws Exception {
        mvc.perform(get("/api/gap-reports").header(HttpHeaders.AUTHORIZATION, learnerToken()))
                .andExpect(status().isBadRequest());
    }

    // --- helpers --------------------------------------------------------------------------------

    private CompetencyFrameworkId registerFramework() {
        CompetencyFrameworkId id = frameworks.register(
                FrameworkFixtures.validCommand("Gap Api " + UUID.randomUUID(), "1.0")).id();
        await().atMost(Duration.ofSeconds(20)).until(() -> projection.existsForFramework(id));
        return id;
    }

    private String analyse(String token, String learnerId) throws Exception {
        String body = mvc.perform(post("/api/gap-reports")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyseBody(learnerId, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String analyseBody(String learnerId, String targetLevelCodes) {
        String targets = targetLevelCodes == null ? "" : ",\"targetLevelCodes\":" + targetLevelCodes;
        return """
                {"learnerId":"%s","frameworkId":"%s"%s}"""
                .formatted(learnerId, framework.value(), targets);
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

    private void recordEvidence(String token, String competencyCode, int ordinal, String levelCode,
                                double confidence) throws Exception {
        mvc.perform(post("/api/learners/me/evidence")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"competencyId":"%s","competencyFrameworkId":"%s","type":"SELF_DECLARED",
                                 "claimedOrdinal":%d,"claimedLevelCode":"%s","confidence":%s,
                                 "source":"api test"}"""
                                .formatted(
                                        CompetencyId.forCompetency(framework, competencyCode).value(),
                                        framework.value(), ordinal, levelCode, confidence)))
                .andExpect(status().isOk());
    }

    private String withoutInstance(String problemJson) throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(problemJson);
        node.remove("instance");
        return node.toString();
    }

    private String learnerToken() throws Exception {
        return tokenFor("test-learner", "test-learner-password");
    }

    private String secondLearnerToken() throws Exception {
        return tokenFor("test-learner-2", "test-learner-2-password");
    }

    private String educatorToken() throws Exception {
        return tokenFor("test-educator", "test-educator-password");
    }

    private String adminToken() throws Exception {
        return tokenFor("test-admin", "test-admin-password");
    }

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("access_token").asText();
    }
}
