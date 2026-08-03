package ie.ul.egas.competency.api;

import java.time.Instant;

/**
 * Integration event announcing that a competency model has been registered and is available to be
 * projected (ADR-007, ADR-022).
 *
 * <p><b>Owned by Competency Modelling</b>, because it states something that happened in this
 * context's domain. Consumers subscribe; none of them owns any part of its definition, and this
 * module gains no dependency by publishing it.
 *
 * <p><b>Named for registration, not publication.</b> {@code ModelStatus.PUBLISHED} exists but no
 * transition sets it, and naming an event for a lifecycle state the system does not implement would
 * put a falsehood in a published contract. ADR-022 records the assumption this rests on — a
 * registered model is an eligible projection source — and leaves an explicit publication workflow
 * as future work. When it arrives, a sibling event joins this one and consumers filter.
 *
 * <p>The event carries the compiled model rather than an identifier alone, so a consumer needs no
 * follow-up call into this context to act on it — which is what keeps the two contexts free of any
 * runtime dependency (ADR-022).
 */
public record CompetencyModelRegistered(
        CompetencyFrameworkId frameworkId,
        Instant registeredAt,
        CompetencyModelSnapshot model) {
}
