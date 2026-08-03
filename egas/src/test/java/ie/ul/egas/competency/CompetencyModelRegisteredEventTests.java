package ie.ul.egas.competency;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.api.CompetencyModelRegistered;
import ie.ul.egas.competency.application.CompetencyFrameworkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registering a framework announces it (ADR-007, ADR-022).
 *
 * <p>This is the half of the integration that Competency Modelling owns: that the event is
 * published, that it carries a compiled model a consumer can act on without calling back, and that
 * nothing EMF-shaped travels with it. Whether a projection is built from it is Gap Analysis's
 * concern and is not yet implemented — Phase 1 deliberately stops at the contract.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class CompetencyModelRegisteredEventTests {

    @Autowired
    CompetencyFrameworkService service;

    @Autowired
    ApplicationEvents events;

    @Test
    void registrationPublishesTheCompiledModel() {
        var registered = service.register(FrameworkFixtures.validCommand("Event Framework", "1.0"));

        List<CompetencyModelRegistered> published =
                events.stream(CompetencyModelRegistered.class).toList();

        assertThat(published).hasSize(1);
        CompetencyModelRegistered event = published.get(0);
        assertThat(event.frameworkId()).isEqualTo(registered.id());
        assertThat(event.registeredAt()).isEqualTo(registered.registeredAt());
        assertThat(event.model().frameworkName()).isEqualTo("Event Framework");
        assertThat(event.model().frameworkVersion()).isEqualTo("1.0");
    }

    @Test
    void theEventCarriesEnoughToProjectWithoutCallingBack() {
        service.register(FrameworkFixtures.validCommand("Self Contained Framework", "1.0"));

        CompetencyModelRegistered event =
                events.stream(CompetencyModelRegistered.class).findFirst().orElseThrow();

        // A consumer must be able to build its projection from the event alone; needing a
        // follow-up query would reintroduce the runtime dependency ADR-022 exists to avoid.
        assertThat(event.model().levels()).isNotEmpty();
        assertThat(event.model().competencies()).isNotEmpty();
        assertThat(event.model().competencies()).allSatisfy(competency -> {
            assertThat(competency.id()).isNotNull();
            assertThat(competency.code()).isNotBlank();
            assertThat(competency.areaCode()).isNotBlank();
        });
    }

    @Test
    void theEventContractIsFreeOfEmfTypes() {
        service.register(FrameworkFixtures.validCommand("EMF Free Framework", "1.0"));

        CompetencyModelRegistered event =
                events.stream(CompetencyModelRegistered.class).findFirst().orElseThrow();

        assertThat(event.toString()).doesNotContain("org.eclipse.emf");
        assertThat(event.model().getClass().getPackageName())
                .isEqualTo("ie.ul.egas.competency.api");
    }
}
