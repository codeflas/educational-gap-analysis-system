package ie.ul.egas.platform.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import ie.ul.egas.platform.security.PrincipalProperties.DevelopmentPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Verifies a development principal's credentials and mints the RS256 JWT that carries them
 * (ADR-013). The single place where "who you are" becomes "what a token says about you".
 *
 * <p><b>Anti-enumeration (plan §6.3).</b> An unknown username and a wrong password are
 * indistinguishable to a caller: both raise {@link BadCredentialsException} with the same
 * message, and — crucially — a BCrypt comparison runs in <em>both</em> paths. Returning early on
 * an unknown username would skip the expensive hash and leave a timing channel that reveals which
 * usernames exist, so an unknown user is checked against a constant dummy hash instead.
 *
 * <p><b>Time comes from the injected {@link Clock}</b> (Step 2's {@code TimeConfiguration},
 * placed there for exactly this), making {@code iat}/{@code exp} deterministic under test.
 * Both are truncated to whole seconds because JWT numeric dates have second granularity — so the
 * expiry this service reports is precisely the expiry encoded in the token, never a hair later.
 */
@Service
class TokenService {

    /**
     * A real BCrypt hash (of a value no principal can supply) used only to spend the same time
     * verifying an unknown username as a known one. It must stay a genuine, well-formed hash:
     * given a malformed one the encoder short-circuits without hashing, which would reopen the
     * very timing channel this constant exists to close. It authenticates nothing.
     */
    private static final String DUMMY_HASH =
            "$2a$10$Adr5l8p85DPxJKlKLxZKs..SIUoMjijkvDDwxEwE67eJ9.FeNi9LG";

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final PrincipalProperties principalProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    TokenService(JwtEncoder jwtEncoder,
                 JwtProperties jwtProperties,
                 PrincipalProperties principalProperties,
                 PasswordEncoder passwordEncoder,
                 Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.principalProperties = principalProperties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Exchanges credentials for a signed token.
     *
     * @throws BadCredentialsException if the username is unknown or the password does not match —
     *         the same exception, with the same message, in both cases
     */
    IssuedToken issue(String username, String rawPassword) {
        DevelopmentPrincipal principal = principalProperties.findByUsername(username).orElse(null);
        String expectedHash = principal == null ? DUMMY_HASH : principal.passwordHash();

        // Evaluated before the null check on purpose: both paths pay the BCrypt cost.
        boolean passwordMatches = passwordEncoder.matches(rawPassword, expectedHash);

        if (principal == null || !passwordMatches) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        return mint(principal);
    }

    private IssuedToken mint(DevelopmentPrincipal principal) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(jwtProperties.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(principal.username())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roleNames(principal))
                .build();

        String tokenValue = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();

        return new IssuedToken(tokenValue, issuedAt, expiresAt);
    }

    /** Sorted so the claim is a deterministic function of the configured role set. */
    private static List<String> roleNames(DevelopmentPrincipal principal) {
        return principal.roles().stream().map(Role::name).sorted().toList();
    }

    /**
     * The outcome of issuance: the serialised token and the window it is valid for.
     *
     * <p>Deliberately not Spring's {@code Jwt}, which models a <em>decoded</em> token — the
     * resource server's vocabulary, complete with header and claim maps. The issuance side has no
     * business handing that surface to a caller: the web adapter needs a value and an expiry to
     * build its response, nothing more. Keeping the boundary this narrow also means the
     * federation path ADR-013 anticipates — an external IdP replacing this endpoint while the
     * resource-server side stays unchanged — can be taken without touching the caller.
     */
    record IssuedToken(String tokenValue, Instant issuedAt, Instant expiresAt) {

        /** Seconds until expiry, as the OAuth2 {@code expires_in} response field expects. */
        long expiresInSeconds() {
            return Math.max(0, expiresAt.getEpochSecond() - issuedAt.getEpochSecond());
        }
    }
}
