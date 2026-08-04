package ie.ul.egas.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The single security choke point: one filter chain, one ordered rule set, deny-by-default
 * preserved as its terminal rule (ADR-010, ADR-015).
 *
 * <p>Decisions recorded here rather than left implicit:
 * <ul>
 *   <li><b>Centralised URL-pattern authorisation (ADR-015)</b> — rather than {@code @PreAuthorize}
 *       scattered across controllers or a filter chain per module. Authorisation stays auditable
 *       in one place, and no security semantics leak into domain or application code. Step 4's
 *       learner-profile <em>ownership</em> checks need principal identity at the application
 *       level; ADR-015 records that as this rule set's designated extension point. Step 5 uses
 *       that extension point a second time for gap reports, where every operation is
 *       learner-scoped and none of it is expressible as a path.</li>
 *   <li><b>CSRF disabled</b> — a pure bearer-token API holds no cookie state, so there is no CSRF
 *       vector. The rationale is stronger now than at Step 1, but the revisit trigger is
 *       unchanged: reintroducing cookie-based state reopens this decision.</li>
 *   <li><b>Stateless sessions</b> — no server-side session affinity, the scalability precondition
 *       ADR-010 promised and the load-test scenario depends on.</li>
 *   <li><b>Bearer entry point and access-denied handler</b> — an absent, malformed, or expired
 *       token yields 401 with a {@code WWW-Authenticate: Bearer} challenge (RFC 6750); a valid
 *       token lacking the required role yields 403. Conflating the two is the classic
 *       resource-server defect, so each gets its own handler and its own tests.</li>
 *   <li><b>Health/info and API docs anonymous</b> — unchanged from Step 1; documentation is not a
 *       protected asset in this single-tenant prototype, and every business endpoint stays
 *       authenticated.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * The claim minted by {@code TokenService}. Named here and nowhere else so the
     * claim-to-authority contract has exactly one definition site.
     */
    private static final String ROLES_CLAIM = "roles";

    /**
     * Spring Security's convention: {@code hasRole("EDUCATOR")} tests for the authority
     * {@code ROLE_EDUCATOR}. The prefix is applied here, at the one mapping site, and the
     * {@code roles} claim therefore travels <em>without</em> it — a mismatch between these two
     * halves produces silent 403s, which is why both are stated explicitly rather than relying
     * on defaults.
     */
    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthenticationConverter converter)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // API *documentation* is not a protected asset in this single-tenant
                        // academic prototype; every business endpoint stays authenticated.
                        // Production profile may disable springdoc entirely (springdoc.api-docs.enabled=false).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // The only new permit of Step 3: without it the endpoint that issues
                        // tokens would itself demand one.
                        .requestMatchers(HttpMethod.POST, "/auth/token").permitAll()
                        // Reading the registry is open to any authenticated principal; changing
                        // it is not. GET is matched first so the role rule below covers exactly
                        // the mutating verbs.
                        .requestMatchers(HttpMethod.GET, "/api/frameworks/**").authenticated()
                        .requestMatchers("/api/frameworks/**").hasAnyRole(
                                Role.EDUCATOR.name(), Role.ADMIN.name())
                        // Learner profiles (ADR-015 Amendment 1). Listing every profile is a
                        // coarse, role-shaped question and stays here. Everything else under
                        // /api/learners/** is admitted on authentication alone, because the chain
                        // cannot see whose profile is being requested — ownership is decided in
                        // LearnerProfileService against the loaded aggregate. Order is
                        // semantically significant: /api/learners/** also matches /api/learners,
                        // so the list rule must precede it.
                        .requestMatchers(HttpMethod.GET, "/api/learners").hasAnyRole(
                                Role.EDUCATOR.name(), Role.ADMIN.name())
                        .requestMatchers("/api/learners/**").authenticated()
                        // Gap reports (ADR-015 Amendment 2). No role rule, and its absence is the
                        // decision: every operation here is scoped to a particular learner, and
                        // "may this caller act for that learner" is precisely the predicate a URL
                        // pattern cannot express. All three are admitted on authentication and
                        // decided in GapAnalysisService — as a 404 when a report was looked up and
                        // must not be disclosed, as a 403 when the learner identifier came from the
                        // caller and was never looked up. Stated explicitly rather than left to the
                        // terminal rule, so the whole policy stays readable as one ordered list.
                        .requestMatchers("/api/gap-reports/**").authenticated()
                        .anyRequest().authenticated())
                .build();
    }

    /**
     * Maps the {@code roles} claim onto Spring authorities. The default converter would read
     * {@code scope}/{@code scp} and prefix {@code SCOPE_}; ADR-013 issues roles rather than OAuth
     * scopes, so both the claim name and the prefix are set explicitly.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix(ROLE_PREFIX);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
