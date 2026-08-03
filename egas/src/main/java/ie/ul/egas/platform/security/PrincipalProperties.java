package ie.ul.egas.platform.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration-backed development principals (ADR-013).
 *
 * <p>Identity persistence — accounts, registration, credential storage — is intentionally outside
 * Step 3 scope. These principals are a development-time construct: usernames, BCrypt password
 * hashes and role sets held in configuration, environment-overridable, so that a real identity
 * source can replace them later without changing the token contract consumers depend on.
 *
 * <p>Validation is deliberately strict and runs at binding time, in the same fail-fast spirit as
 * the key policy: a blank field, a plaintext password pasted where a hash belongs, an empty role
 * set, or a duplicated username is always an operator error, and all four are far cheaper to
 * discover at startup than at first login.
 *
 * <p>An <em>empty</em> roster is not an error: it simply means no one can authenticate, which
 * stays a valid state if an external identity source ever supersedes this mechanism.
 */
@ConfigurationProperties(prefix = "egas.security")
public record PrincipalProperties(List<DevelopmentPrincipal> principals) {

    public PrincipalProperties {
        principals = principals == null ? List.of() : List.copyOf(principals);

        Set<String> seen = new LinkedHashSet<>();
        for (DevelopmentPrincipal principal : principals) {
            if (!seen.add(principal.username())) {
                throw new IllegalArgumentException(
                        "egas.security.principals contains a duplicate username: '"
                                + principal.username() + "'");
            }
        }
    }

    /**
     * Reserved prefix marking a principal as a development credential. Configuration carrying it
     * is committed to the repository and therefore public knowledge; {@code PrincipalConfiguration}
     * refuses to start outside the dev profile when any principal bears it.
     */
    public static final String DEVELOPMENT_USERNAME_PREFIX = "dev-";

    /** The principal with this exact username, if one is configured. */
    public Optional<DevelopmentPrincipal> findByUsername(String username) {
        return principals.stream()
                .filter(principal -> principal.username().equals(username))
                .findFirst();
    }

    /**
     * Usernames carrying {@link #DEVELOPMENT_USERNAME_PREFIX}. Kept here as a pure query so the
     * profile-dependent <em>policy</em> stays in the wiring layer, mirroring how
     * {@code JwtKeyMaterial} takes an explicit flag rather than reading the Environment.
     */
    public List<String> developmentUsernames() {
        return principals.stream()
                .map(DevelopmentPrincipal::username)
                .filter(username -> username.startsWith(DEVELOPMENT_USERNAME_PREFIX))
                .toList();
    }

    /**
     * One configured principal. {@code passwordHash} is a BCrypt hash — never a plaintext
     * password — and is checked for the BCrypt prefix so the difference cannot pass unnoticed.
     */
    public record DevelopmentPrincipal(String username, String passwordHash, Set<Role> roles) {

        public DevelopmentPrincipal {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException(
                        "egas.security.principals[].username must not be blank");
            }
            if (passwordHash == null || passwordHash.isBlank()) {
                throw new IllegalArgumentException(
                        "egas.security.principals[].password-hash must not be blank (principal '"
                                + username + "')");
            }
            if (!passwordHash.startsWith("$2a$")
                    && !passwordHash.startsWith("$2b$")
                    && !passwordHash.startsWith("$2y$")) {
                throw new IllegalArgumentException(
                        "egas.security.principals[].password-hash must be a BCrypt hash, not a "
                                + "plaintext password (principal '" + username + "')");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalArgumentException(
                        "egas.security.principals[].roles must not be empty (principal '"
                                + username + "')");
            }
            roles = Set.copyOf(roles);
        }
    }
}
