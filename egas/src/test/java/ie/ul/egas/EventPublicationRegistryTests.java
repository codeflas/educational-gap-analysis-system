package ie.ul.egas;

import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.api.CompetencyModelRegistered;
import ie.ul.egas.competency.application.CompetencyFrameworkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.ApplicationModuleListener;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the durable publication registry works against the migrated schema (ADR-011 Amendment 1,
 * ADR-022) — that the application starts, that a published event is written to
 * {@code common.event_publication}, and that Spring Modulith reads it back and completes it without
 * a schema or mapping error.
 *
 * <p><b>Why a listener lives here.</b> Modulith writes a publication row only when a transactional
 * listener is registered for the event; with no consumer, publishing is a no-op as far as the
 * registry is concerned, so the other Phase 1 tests prove the event is <em>published</em> and prove
 * nothing about the registry. Gap Analysis is out of scope for this phase, so the consumer is
 * test-only scaffolding: it exercises the mechanism without committing production code to a module
 * this phase does not touch.
 *
 * <p><b>What this is really checking</b> is the {@code text} column. Hibernate's generated DDL maps
 * the entity's unannotated {@code String} to {@code varchar(255)}; the migration deliberately
 * deviates, because a serialised {@code CompetencyModelRegistered} carries a whole compiled model.
 * The assertion on serialised length is the evidence that the deviation was necessary and that the
 * wider column is fully compatible with Modulith's mapping under {@code ddl-auto: validate}.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, EventPublicationRegistryTests.RegistryProbeConfiguration.class})
class EventPublicationRegistryTests {

    private static final String EVENT_TYPE = CompetencyModelRegistered.class.getName();

    @Autowired
    CompetencyFrameworkService service;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    RecordingListener listener;

    @Test
    void publicationIsWrittenToTheRegistryAndCompletedAfterDelivery() {
        var registered = service.register(
                FrameworkFixtures.validCommand("Registry Framework", "1.0"));

        // Delivery is asynchronous (@ApplicationModuleListener), so the consumer is awaited rather
        // than assumed to have run.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(listener.received()).isNotEmpty());

        assertThat(listener.received())
                .extracting(event -> event.frameworkId())
                .contains(registered.id());

        Map<String, Object> row = await().atMost(Duration.ofSeconds(20)).until(
                () -> latestPublication(), publication ->
                        publication != null && publication.get("completion_date") != null);

        assertThat(row.get("event_type")).isEqualTo(EVENT_TYPE);
        assertThat((String) row.get("listener_id"))
                .as("the registry records which listener the delivery was for")
                .contains("RecordingListener");
        assertThat(row.get("completion_date"))
                .as("a completed delivery is marked, which is what makes an incomplete one recoverable")
                .isNotNull();
    }

    @Test
    void theSerialisedEventExceedsTheDefaultVarcharAndStillRoundTrips() {
        service.register(FrameworkFixtures.validCommand("Payload Framework", "1.0"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(listener.received()).isNotEmpty());

        // One row, read once: length and content must describe the same publication. Measuring a
        // max() across rows while reading the latest would compare two different events.
        Map<String, Object> row = jdbc.sql("""
                        select serialized_event, length(serialized_event) as payload_length
                        from common.event_publication
                        where event_type = ? and serialized_event like '%Payload Framework%'
                        order by publication_date desc limit 1""")
                .param(EVENT_TYPE)
                .query()
                .singleRow();

        String serialized = (String) row.get("serialized_event");
        int payloadLength = ((Number) row.get("payload_length")).intValue();

        assertThat(payloadLength)
                .as("a compiled model does not fit Hibernate's default varchar(255); the migration's "
                        + "text column is required, not a preference")
                .isGreaterThan(255);

        // Read back through the same column the registry writes: no truncation, no mapping error.
        assertThat(serialized).contains("frameworkName").contains("competencies");
        assertThat(serialized).contains("Payload Framework");
        assertThat(serialized.length())
                .as("the value read back is the value stored — nothing was truncated")
                .isEqualTo(payloadLength);
    }

    @Test
    void theRegistryTableLivesInTheCommonSchema() {
        // ADR-011 Amendment 1: framework-managed metadata belongs to `common`, not to a business
        // schema and not to `public`. Asserted against the catalogue rather than the migration file,
        // so a change to Hibernate's default schema resolution would fail here.
        List<String> schemas = jdbc.sql("""
                        select table_schema from information_schema.tables
                        where table_name = 'event_publication'""")
                .query(String.class)
                .list();

        assertThat(schemas).containsExactly("common");
    }

    private Map<String, Object> latestPublication() {
        return jdbc.sql("""
                        select event_type, listener_id, completion_date
                        from common.event_publication
                        where event_type = ? order by publication_date desc limit 1""")
                .param(EVENT_TYPE)
                .query()
                .listOfRows().stream()
                .findFirst()
                .orElse(null);
    }

    @TestConfiguration
    static class RegistryProbeConfiguration {

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    /** Test-only consumer: its existence is what makes Modulith persist a publication row. */
    static class RecordingListener {

        private final List<CompetencyModelRegistered> received = new CopyOnWriteArrayList<>();

        @ApplicationModuleListener
        void on(CompetencyModelRegistered event) {
            received.add(event);
        }

        List<CompetencyModelRegistered> received() {
            return received;
        }
    }
}
