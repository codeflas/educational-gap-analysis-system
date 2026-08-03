package ie.ul.egas.gapanalysis;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.competency.api.CompetencyModelRegistered;
import ie.ul.egas.competency.application.CompetencyFrameworkService;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The integration itself: registering a framework in Competency Modelling populates Gap Analysis's
 * projection, with no call passing between the two contexts (ADR-007, ADR-022).
 *
 * <p>This is the only test that proves the <em>wiring</em> rather than one of its halves. Phase 1
 * proved an event is published; the adapter tests prove a projection can be written and read. Both
 * could pass while nothing connected them — which is exactly the defect an integration is prone to
 * and the reason this test exists.
 *
 * <p>Delivery is asynchronous, so the assertion is awaited rather than assumed. That is not test
 * scaffolding hiding a race: it is the eventual consistency ADR-007 accepts, made visible.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CompetencyModelProjectionIntegrationTests {

    @Autowired
    CompetencyFrameworkService frameworks;

    @Autowired
    CompetencyModelProjectionRepository projection;

    @Autowired
    JdbcClient jdbc;

    @Test
    void registeringAFrameworkPopulatesTheProjection() {
        var registered = frameworks.register(
                FrameworkFixtures.validCommand("Projected End To End", "1.0"));

        ProjectedCompetencyModel model = await().atMost(Duration.ofSeconds(20))
                .until(() -> projection.findByFrameworkId(registered.id()).orElse(null),
                        projected -> projected != null);

        assertThat(model.frameworkName()).isEqualTo("Projected End To End");
        assertThat(model.frameworkVersion()).isEqualTo("1.0");
        // Truncated to microseconds, the precision timestamptz stores: an untruncated instant is
        // rounded by PostgreSQL and would read back different from the value written.
        assertThat(model.registeredAt())
                .isEqualTo(registered.registeredAt().truncatedTo(ChronoUnit.MICROS));
        assertThat(model.projectedAt())
                .as("projection lag is observable: written after the model was registered")
                .isAfterOrEqualTo(registered.registeredAt().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void theProjectionCarriesTheFlattenedModelGapComputationWillRead() {
        var registered = frameworks.register(
                FrameworkFixtures.validCommand("Projected Content", "1.0"));

        ProjectedCompetencyModel model = awaitProjection(registered.id());

        assertThat(model.levels()).extracting(ProjectedLevel::code).containsExactly("L1", "L2", "L3");
        assertThat(model.levelByCode("L3")).map(ProjectedLevel::ordinal).contains(3);
        assertThat(model.highestLevel()).map(ProjectedLevel::code).contains("L3");

        assertThat(model.competencies()).extracting(ProjectedCompetency::code)
                .containsExactly("SE-ARC", "SE-DSN", "SE-TST");
        assertThat(model.competencies()).extracting(ProjectedCompetency::areaCode)
                .containsExactly("DES", "DES", "QUA");
    }

    @Test
    void projectedCompetenciesCarryTheDerivedIdentityALearnerAssertionWouldName() {
        // The join key. A learner's assertion names a CompetencyId; without the derived identity
        // reaching the projection, gap computation would have nothing to match it against.
        var registered = frameworks.register(
                FrameworkFixtures.validCommand("Projected Identity", "1.0"));

        ProjectedCompetencyModel model = awaitProjection(registered.id());

        assertThat(model.competencies()).allSatisfy(competency ->
                assertThat(competency.id())
                        .isEqualTo(CompetencyId.forCompetency(registered.id(), competency.code())));
    }

    @Test
    void theProjectionRecordsAvailableLevelsNotRequirements() {
        var registered = frameworks.register(
                FrameworkFixtures.validCommand("Projected Levels", "1.0"));

        ProjectedCompetencyModel model = awaitProjection(registered.id());

        assertThat(levelsFor(model, "SE-DSN")).containsExactly("L2");
        assertThat(levelsFor(model, "SE-TST")).containsExactly("L1");
        assertThat(levelsFor(model, "SE-ARC"))
                .as("a competency defining no levels survives projection as exactly that")
                .isEmpty();
    }

    @Test
    void deliveryTravelsTheDurableRegistryRatherThanADirectCall() {
        // Answers a question the other tests leave open: is the listener actually reached through
        // Modulith's publication registry, or merely invoked in-process? A registry row naming this
        // listener proves the former — and its completion_date is set only after the consuming
        // transaction commits, which is what makes a failed projection recoverable rather than lost.
        var registered = frameworks.register(
                FrameworkFixtures.validCommand("Projected Via Registry", "1.0"));
        awaitProjection(registered.id());

        Map<String, Object> publication = await().atMost(Duration.ofSeconds(20)).until(
                () -> jdbc.sql("""
                                select listener_id, completion_date from common.event_publication
                                where event_type = ? and serialized_event like '%Projected Via Registry%'
                                order by publication_date desc limit 1""")
                        .param(CompetencyModelRegistered.class.getName())
                        .query().listOfRows().stream().findFirst().orElse(null),
                row -> row != null && row.get("completion_date") != null);

        assertThat((String) publication.get("listener_id"))
                .as("the registry records the projection listener as the delivery target")
                .contains("CompetencyModelProjectionListener");
    }

    @Test
    void separateFrameworksAreProjectedIndependently() {
        var first = frameworks.register(FrameworkFixtures.validCommand("Projected First", "1.0"));
        var second = frameworks.register(FrameworkFixtures.validCommand("Projected Second", "1.0"));

        ProjectedCompetencyModel firstModel = awaitProjection(first.id());
        ProjectedCompetencyModel secondModel = awaitProjection(second.id());

        assertThat(firstModel.frameworkName()).isEqualTo("Projected First");
        assertThat(secondModel.frameworkName()).isEqualTo("Projected Second");
        assertThat(firstModel.competencies()).hasSize(3);
        assertThat(secondModel.competencies())
                .as("one framework's projection must not absorb another's competencies")
                .hasSize(3);
    }

    private ProjectedCompetencyModel awaitProjection(
            ie.ul.egas.competency.api.CompetencyFrameworkId frameworkId) {
        return await().atMost(Duration.ofSeconds(20))
                .until(() -> projection.findByFrameworkId(frameworkId).orElse(null),
                        projected -> projected != null);
    }

    private java.util.List<String> levelsFor(ProjectedCompetencyModel model, String code) {
        return model.competencies().stream()
                .filter(competency -> competency.code().equals(code))
                .findFirst().orElseThrow()
                .definedLevelCodes();
    }
}
