package ie.ul.egas.gapanalysis.application;

import ie.ul.egas.gapanalysis.domain.policy.GapSeverityPolicy;
import ie.ul.egas.gapanalysis.domain.policy.OrdinalDistanceSeverityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Module composition. Domain classes carry no Spring annotations (ArchUnit-enforced), so the domain
 * service implementation is instantiated here at the application boundary — exactly as
 * {@code LearnerModuleConfiguration} does for {@code LevelResolutionPolicy} and
 * {@code CompetencyModuleConfiguration} for {@code ConformanceValidator}.
 *
 * <p>This is where ADR-021's substitutability stops being theoretical. Replacing the system's
 * judgement about how serious a gap is — grading absence more harshly than any shortfall, or
 * weighting by the confidence of the evidence behind an attainment — is a change to this one bean
 * method. It touches neither the aggregate, nor the schema, nor the API, which is the property the
 * port was created to provide and the third independent instance of the technique after ADR-006 and
 * ADR-018.
 */
@Configuration
class GapAnalysisModuleConfiguration {

    @Bean
    GapSeverityPolicy gapSeverityPolicy() {
        return new OrdinalDistanceSeverityPolicy();
    }
}
