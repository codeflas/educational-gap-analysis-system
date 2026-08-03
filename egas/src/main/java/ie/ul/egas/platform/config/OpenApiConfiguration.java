package ie.ul.egas.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API document metadata (ADR-009). Endpoint documentation itself lives with the controllers.
 *
 * <p>The bearer scheme declared here is what turns Swagger UI into a usable client for a
 * deny-by-default API: it renders the Authorize dialog, and the global requirement marks every
 * operation as needing the token. {@code POST /auth/token} opts out at its own declaration site —
 * it is where tokens are obtained, so requiring one would be circular.
 *
 * <p>The published document contains no secrets: a security <em>scheme</em> describes how to
 * present a credential, never a credential itself.
 */
@Configuration
class OpenApiConfiguration {

    /** Referenced by the global requirement below; springdoc keys the Authorize dialog on it. */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI egasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EGAS API")
                        .version("v1")
                        .description("Educational Gap Analysis System — model-driven competency gap analysis "
                                + "with AI-based recommendation support. MSc dissertation prototype, "
                                + "University of Limerick."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("RS256 JWT issued by POST /auth/token (ADR-013). Paste the "
                                + "access_token value; Swagger UI adds the Bearer prefix.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
