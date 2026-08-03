package ie.ul.egas.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Baseline security posture: deny-by-default from the first commit (secure-by-design, ADR-010).
 *
 * <p>Decisions recorded here rather than left implicit:
 * <ul>
 *   <li><b>CSRF disabled</b> — this is a stateless bearer-token API; no session cookie means no
 *       CSRF vector. The decision MUST be revisited if cookie-based state is ever introduced.</li>
 *   <li><b>Stateless sessions</b> — horizontal scalability requires no server-side session
 *       affinity; this is a precondition for the load-test evaluation scenario.</li>
 *   <li><b>Explicit 401 entry point</b> — with form login and HTTP Basic disabled, Spring
 *       Security would otherwise fall back to a 403 for unauthenticated requests, mislabelling
 *       an authentication failure as an authorisation failure.</li>
 *   <li><b>Health/info probes anonymous</b> — required by container orchestration and the
 *       CI smoke tests; expose nothing sensitive.</li>
 * </ul>
 *
 * <p>Extension point (Step 3, ADR-010): replace the entry point with a bearer-token variant and
 * add {@code oauth2ResourceServer(jwt)} with a locally issued RSA-signed JWT. The deny-by-default
 * rule below is intentionally never relaxed.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // API *documentation* is not a protected asset in this single-tenant
                        // academic prototype; every business endpoint stays authenticated.
                        // Production profile may disable springdoc entirely (springdoc.api-docs.enabled=false).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
