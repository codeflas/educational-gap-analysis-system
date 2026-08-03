package ie.ul.egas.platform.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Principals and the means of verifying their credentials — kept separate from
 * {@link JwtConfiguration}, which owns signing key material. The two concerns fail for different
 * reasons and evolve independently: key provisioning is an operational matter, whereas the
 * principal roster is the part ADR-013 expects a real identity source to replace.
 *
 * <p>BCrypt at its default strength is the deliberate choice: verification is intentionally slow,
 * which both blunts offline attacks on the configured hashes and makes the equal-cost credential
 * check in {@code TokenService} genuinely equal.
 *
 * <p><b>Development principals fail loudly outside dev.</b> The roster lives in
 * {@code application-dev.yml} and so cannot reach a deployed instance through the configuration
 * files alone; the guard below is the second line, catching the case where a {@code dev-}
 * principal arrives some other way — reinstated in the default configuration, injected through
 * the environment, or carried by an unexpected profile combination. Committed development
 * credentials are public knowledge, so an instance holding one outside dev is compromised the
 * moment it starts. This is the credential-side counterpart of the ADR-013/A5 key policy: the
 * failure that matters most is the silent one.
 */
@Configuration
@EnableConfigurationProperties(PrincipalProperties.class)
class PrincipalConfiguration {

    private static final String DEV_PROFILE = "dev";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Applies the roster policy at context refresh, so a violation aborts startup rather than
     * surfacing at first login. Returns a marker rather than mutating state: the bean exists to
     * give the check a lifecycle, and its creation <em>is</em> the check.
     */
    @Bean
    PrincipalPolicy principalPolicy(PrincipalProperties properties, Environment environment) {
        boolean developmentPrincipalsPermitted = environment.acceptsProfiles(Profiles.of(DEV_PROFILE));
        List<String> developmentUsernames = properties.developmentUsernames();

        if (!developmentUsernames.isEmpty() && !developmentPrincipalsPermitted) {
            throw new IllegalStateException(
                    "Refusing to start: development principals " + developmentUsernames
                            + " are configured but the 'dev' profile is not active. Usernames "
                            + "prefixed '" + PrincipalProperties.DEVELOPMENT_USERNAME_PREFIX
                            + "' are committed to the repository and their passwords are public "
                            + "knowledge (ADR-013). Override egas.security.principals for this "
                            + "environment, or activate the 'dev' profile for local development.");
        }
        return new PrincipalPolicy(developmentUsernames.size(), developmentPrincipalsPermitted);
    }

    /** Records what the policy admitted, so the decision is inspectable rather than implicit. */
    record PrincipalPolicy(int developmentPrincipalCount, boolean developmentPrincipalsPermitted) {
    }
}
