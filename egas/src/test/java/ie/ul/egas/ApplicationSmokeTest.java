package ie.ul.egas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walking-skeleton smoke test: the application boots against a real database, Flyway applies
 * the module-schema baseline, and the security posture behaves as specified from commit one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void bootsAgainstRealDatabaseAndAppliesModuleSchemas() {
        var schemas = jdbc.sql("select schema_name from information_schema.schemata")
                .query(String.class)
                .list();

        assertThat(schemas).contains("competency", "learner", "catalogue", "gap_analysis", "recommendation");
    }

    @Test
    void deniesUnauthenticatedRequestsByDefault() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                // RFC 6750: a 401 must tell the client how to authenticate, not merely that it
                // failed. The Step-1 entry point returned a bare status; the bearer one does not.
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void exposesHealthProbeAnonymously() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
