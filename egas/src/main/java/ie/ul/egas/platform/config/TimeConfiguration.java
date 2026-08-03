package ie.ul.egas.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable {@link Clock} (UTC) so time-dependent domain behaviour is deterministic
 * under test — aggregates take Clock, never call Instant.now() directly.
 */
@Configuration
class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
