package ie.ul.egas.platform.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The issued bearer token, shaped as an OAuth2 access-token response (RFC 6749 §5.1) even though
 * ADR-013 deliberately declines the full OAuth2 machinery: the field names are what every HTTP
 * client, Swagger UI included, already knows how to read, and matching them costs nothing.
 *
 * <p>{@code expires_in} is a duration in seconds rather than an absolute instant — the RFC's
 * choice, and the one that spares clients any clock-skew arithmetic. No refresh token and no
 * scope: neither exists in this design (ADR-013 records both as deliberate omissions).
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {

    private static final String BEARER = "Bearer";

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, BEARER, expiresInSeconds);
    }
}
