package ie.ul.egas.competency;

import com.fasterxml.jackson.databind.ObjectMapper;
import ie.ul.egas.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-slice HTTP contract against the real stack (Spring context + PostgreSQL 16 via
 * Testcontainers): status codes, Location header, RFC 9457 bodies with the violations
 * extension, and the security posture. Tests authenticate via spring-security-test rather than
 * weakening deny-by-default; each test uses a unique framework name (shared context, no
 * per-test rollback across HTTP calls by design).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CompetencyFrameworkApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registersAConformingFrameworkWithCreatedStatusAndLocation() throws Exception {
        var request = FrameworkFixtures.validRequest("API Register Framework", "1.0");

        mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("API Register Framework"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void returnsTheFullModelTreeOnDetail() throws Exception {
        var request = FrameworkFixtures.validRequest("API Detail Framework", "1.0");
        String body = mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mvc.perform(get("/api/frameworks/{id}", id).with(user("educator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levels.length()").value(2))
                .andExpect(jsonPath("$.areas[0].code").value("DES"))
                .andExpect(jsonPath("$.areas[0].competencies[1].code").value("SE-ARC"))
                .andExpect(jsonPath("$.areas[0].competencies[1].prerequisites[0]").value("SE-DSN"))
                .andExpect(jsonPath("$.areas[0].competencies[0].levelDescriptors[0].levelCode").value("L2"));
    }

    @Test
    void listsRegisteredFrameworks() throws Exception {
        var request = FrameworkFixtures.validRequest("API List Framework", "1.0");
        mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/frameworks").with(user("educator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("API List Framework")));
    }

    @Test
    void rejectsNonConformingModelsWithProblemDetailAndViolations() throws Exception {
        var request = FrameworkFixtures.requestWithPrerequisiteCycle("API Cyclic Framework");

        mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Model does not conform to the competency metamodel"))
                .andExpect(jsonPath("$.violations[*].code", hasItem("CYCLIC_PREREQUISITES")));
    }

    @Test
    void rejectsDuplicateNameAndVersionWithConflict() throws Exception {
        var request = FrameworkFixtures.validRequest("API Duplicate Framework", "1.0");
        mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownIds() throws Exception {
        mvc.perform(get("/api/frameworks/{id}", UUID.randomUUID()).with(user("educator")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMalformedPayloadsWithBadRequest() throws Exception {
        var request = FrameworkFixtures.validRequest("   ", "1.0"); // blank name: transport tier

        mvc.perform(post("/api/frameworks")
                        .with(user("educator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesUnauthenticatedAccess() throws Exception {
        mvc.perform(get("/api/frameworks")).andExpect(status().isUnauthorized());
    }
}
