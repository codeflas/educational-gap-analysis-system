package ie.ul.egas.gapanalysis.infrastructure.projection;

import ie.ul.egas.competency.api.CompetencyModelRegistered;
import ie.ul.egas.competency.api.CompetencyModelSnapshot;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Consumes {@link CompetencyModelRegistered} and rebuilds this context's projection of the model
 * (ADR-007, ADR-022).
 *
 * <p><b>An adapter, not application logic.</b> Event delivery is a delivery mechanism exactly as
 * HTTP is, so this lives in the infrastructure ring beside the persistence adapter rather than in a
 * service. It makes no decision: it translates a published snapshot into this context's read model
 * and hands it to a port.
 *
 * <p><b>Asynchronous and transactional</b>, which {@link ApplicationModuleListener} gives together.
 * Asynchronous because a projection failure must not roll back the registration that triggered it —
 * the two contexts are independent and a bad consumer should not be able to reject a producer's
 * work. Transactional because the durable registry only marks a delivery complete when the
 * consuming transaction commits, which is what makes a failure recoverable rather than lost.
 *
 * <p><b>Redelivery is expected, not exceptional.</b> Modulith resubmits incomplete publications, so
 * this may run more than once for the same event; the port's replace semantics make that harmless.
 * Idempotency is a property of the write, not something asserted by the caller.
 *
 * <p>No {@code EObject} appears here or could: the event carries records, compiled inside
 * Competency Modelling precisely so that EMF stops at that boundary (ADR-012).
 */
@Component
class CompetencyModelProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(CompetencyModelProjectionListener.class);

    private final CompetencyModelProjectionRepository projection;
    private final Clock clock;

    CompetencyModelProjectionListener(CompetencyModelProjectionRepository projection, Clock clock) {
        this.projection = projection;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(CompetencyModelRegistered event) {
        ProjectedCompetencyModel model = toProjection(event);
        projection.project(model);

        log.info("Projected competency model {} ({} competencies, {} levels) registered at {}.",
                event.frameworkId().value(), model.competencies().size(), model.levels().size(),
                event.registeredAt());
    }

    /**
     * Timestamps are truncated to microseconds because that is the precision {@code timestamptz}
     * stores. Java instants carry nanoseconds, so an untruncated value would be silently rounded by
     * PostgreSQL and read back different from the one written — a projection that did not equal
     * itself across a round trip. Truncating here makes the stored value deterministic and makes
     * the loss of precision a decision rather than an accident of the driver.
     *
     * <p>The consequence is worth stating: a projection's {@code registeredAt} may differ from the
     * event's by under a microsecond. Nothing compares them for equality — the field exists to make
     * projection lag observable — but a future consumer that did would need to know.
     */
    private ProjectedCompetencyModel toProjection(CompetencyModelRegistered event) {
        CompetencyModelSnapshot snapshot = event.model();
        return new ProjectedCompetencyModel(
                event.frameworkId(),
                snapshot.frameworkName(),
                snapshot.frameworkVersion(),
                event.registeredAt().truncatedTo(ChronoUnit.MICROS),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                snapshot.levels().stream()
                        .map(level -> new ProjectedLevel(level.code(), level.name(), level.ordinal()))
                        .toList(),
                snapshot.competencies().stream().map(this::toProjection).toList());
    }

    private ProjectedCompetency toProjection(CompetencyModelSnapshot.Competency competency) {
        return new ProjectedCompetency(
                competency.id(),
                competency.code(),
                competency.name(),
                competency.areaCode(),
                competency.definedLevelCodes());
    }
}
