package ie.ul.egas.platform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
@EnableConfigurationProperties(PrincipalProperties.class)
class PrincipalConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
