package ie.ul.egas.platform.security;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The credential-side counterpart of the A5 key policy: development principals must not reach an
 * instance running outside the dev profile. Their passwords are committed to this repository, so
 * such an instance is compromised from the moment it starts — and, unlike a missing key, nothing
 * about it looks wrong at runtime. Hence a startup abort rather than a log line.
 *
 * <p>Scoped to {@link PrincipalConfiguration} on {@link ApplicationContextRunner}: the behaviour
 * under test is context refresh succeeding or failing, which needs no web server or database.
 */
class PrincipalConfigurationTests {

    private static final String BCRYPT_HASH =
            "$2a$10$ZFQB0PhlYmITvFQK5Dt24uD5s2/c1IS2IVFp6ssAFkQRYt9D8GJ/S";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PrincipalConfiguration.class);

    @Test
    void refusesToStartWithDevelopmentPrincipalsOutsideTheDevProfile() {
        runnerWith("dev-educator:EDUCATOR")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Refusing to start")
                            .hasStackTraceContaining("dev-educator");
                });
    }

    @Test
    void admitsDevelopmentPrincipalsUnderTheDevProfile() {
        runnerWith("dev-educator:EDUCATOR", "dev-admin:ADMIN")
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PrincipalConfiguration.PrincipalPolicy.class)
                            .developmentPrincipalCount()).isEqualTo(2);
                });
    }

    @Test
    void admitsOperatorSuppliedPrincipalsOutsideDev() {
        runnerWith("registrar:ADMIN")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                    assertThat(context.getBean(PrincipalConfiguration.PrincipalPolicy.class)
                            .developmentPrincipalCount())
                            .as("the guard must not fire on legitimate principals")
                            .isZero();
                });
    }

    @Test
    void startsWithAnEmptyRosterAndAuthenticatesNobody() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PrincipalProperties.class).principals()).isEmpty();
        });
    }

    @Test
    void identifiesDevelopmentUsernamesByTheReservedPrefix() {
        runnerWith("dev-learner:LEARNER", "registrar:ADMIN")
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context.getBean(PrincipalProperties.class).developmentUsernames())
                        .containsExactly("dev-learner"));
    }

    /**
     * Expands {@code "username:ROLE"} specifications into the indexed property lines the binder
     * expects, so each test states only what it is actually about.
     */
    private ApplicationContextRunner runnerWith(String... principalSpecs) {
        List<String> properties = new ArrayList<>();
        for (int index = 0; index < principalSpecs.length; index++) {
            String[] parts = principalSpecs[index].split(":");
            String prefix = "egas.security.principals[" + index + "].";
            properties.add(prefix + "username=" + parts[0]);
            properties.add(prefix + "password-hash=" + BCRYPT_HASH);
            properties.add(prefix + "roles=" + parts[1]);
        }
        return contextRunner.withPropertyValues(properties.toArray(String[]::new));
    }
}
