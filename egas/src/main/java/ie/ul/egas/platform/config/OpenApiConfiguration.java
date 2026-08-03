package ie.ul.egas.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API document metadata (ADR-009). Endpoint documentation itself lives with the controllers. */
@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI egasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EGAS API")
                .version("v1")
                .description("Educational Gap Analysis System — model-driven competency gap analysis "
                        + "with AI-based recommendation support. MSc dissertation prototype, "
                        + "University of Limerick."));
    }
}
