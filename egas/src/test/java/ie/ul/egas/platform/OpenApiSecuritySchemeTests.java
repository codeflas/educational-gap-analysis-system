package ie.ul.egas.platform;

import ie.ul.egas.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The published OpenAPI document advertises the bearer scheme (DoD #5's precondition: no scheme,
 * no Authorize dialog) and exempts the token endpoint from it.
 *
 * <p>Asserted against the generated document rather than the configuration bean, because the
 * failure that matters is a document Swagger UI cannot drive — annotations that never reach the
 * output would satisfy a bean-level check while leaving the UI unusable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSecuritySchemeTests {

    @Autowired
    MockMvc mvc;

    @Test
    void publishesABearerJwtSecuritySchemeAndRequiresItGlobally() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void exemptsTheTokenEndpointFromTheBearerRequirement() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths.['/auth/token'].post.security").isArray())
                .andExpect(jsonPath("$.paths.['/auth/token'].post.security", hasSize(0)));
    }
}
