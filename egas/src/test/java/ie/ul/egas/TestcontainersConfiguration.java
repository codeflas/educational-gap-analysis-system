package ie.ul.egas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-database test infrastructure. {@code @ServiceConnection} wires the container's JDBC
 * coordinates into the Spring context, so tests run against genuine PostgreSQL 16 — the same
 * image as compose.yaml — rather than an in-memory imitation whose SQL dialect would diverge
 * from production (test fidelity over test convenience).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
