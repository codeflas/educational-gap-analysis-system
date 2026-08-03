package ie.ul.egas.learner.application;

import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Module composition. Domain classes carry no Spring annotations (ArchUnit-enforced); the domain
 * service implementation is instantiated here, at the application boundary, keeping the domain ring
 * framework-free while still participating in dependency injection — exactly as
 * {@code CompetencyModuleConfiguration} does for {@code ConformanceValidator}.
 *
 * <p>This is also where ADR-018's substitutability stops being theoretical: replacing the system's
 * judgement about how evidence collapses into a proficiency level is a change to this one bean
 * method, touching neither the aggregate, the schema, nor the API.
 */
@Configuration
class LearnerModuleConfiguration {

    @Bean
    LevelResolutionPolicy levelResolutionPolicy() {
        return new HighestConfidenceResolutionPolicy();
    }
}
