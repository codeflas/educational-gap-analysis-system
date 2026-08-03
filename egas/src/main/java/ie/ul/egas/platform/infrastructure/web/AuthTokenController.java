package ie.ul.egas.platform.infrastructure.web;

import ie.ul.egas.platform.infrastructure.web.dto.TokenRequest;
import ie.ul.egas.platform.infrastructure.web.dto.TokenResponse;
import ie.ul.egas.platform.security.TokenService;
import ie.ul.egas.platform.security.TokenService.IssuedToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving web adapter for token issuance (ADR-013). As thin as every other controller here: it
 * binds and validates the payload, delegates, and shapes the response — no credential logic of
 * its own, so the anti-enumeration guarantee stays where it can be reasoned about, in
 * {@link TokenService}.
 *
 * <p><b>Placement (ADR-014).</b> The platform module keeps its controllers under
 * {@code platform.infrastructure.web}, which satisfies the {@code restControllersOnlyInWebAdapters}
 * fitness function unmodified — the rule is honoured by construction rather than exempted.
 *
 * <p>The endpoint is still behind the deny-by-default filter chain at this point; the
 * {@code permitAll} that makes it reachable without a token arrives with the SecurityConfig
 * rewrite, which is the only new permit that step introduces.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication",
        description = "Token issuance for development principals (ADR-013): exchange credentials "
                + "for a short-lived RS256 bearer token")
class AuthTokenController {

    private final TokenService tokenService;

    AuthTokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange credentials for a bearer token",
            description = "Returns an RS256 JWT carrying the principal's roles. Invalid credentials "
                    + "are rejected uniformly: an unknown username and a wrong password are "
                    + "indistinguishable in both body and timing.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "400", description = "Malformed request payload")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    // Empty on purpose: overrides the document-wide bearer requirement. Demanding a token from
    // the endpoint that issues them would be circular, and Swagger UI would refuse to call it
    // before the user has one.
    @SecurityRequirements
    TokenResponse token(@Valid @RequestBody TokenRequest request) {
        IssuedToken issued = tokenService.issue(request.username(), request.password());
        return TokenResponse.bearer(issued.tokenValue(), issued.expiresInSeconds());
    }
}
