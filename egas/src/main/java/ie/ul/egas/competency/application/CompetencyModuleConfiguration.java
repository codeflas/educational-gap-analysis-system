package ie.ul.egas.competency.application;

import ie.ul.egas.competency.domain.validation.ConformanceValidator;
import ie.ul.egas.competency.domain.validation.EmfConformanceValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Module composition. Domain classes carry no Spring annotations (ArchUnit-enforced); the
 * domain service implementation is instantiated here, at the application boundary, keeping the
 * domain ring framework-free while still participating in dependency injection.
 */
@Configuration
class CompetencyModuleConfiguration {

    @Bean
    ConformanceValidator conformanceValidator() {
        return new EmfConformanceValidator();
    }
}
