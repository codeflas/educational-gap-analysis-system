# EGAS Step 5 — Gap Analysis: Implementation Plan

**Status:** APPROVED for implementation — ADR work complete, no code authorised until the phase gate
**Date:** 3 August 2026
**Baseline:** Step 4 complete at `905b3b7`; 152 tests green; ADRs 001–022 recorded (007 recovered)
**Governing decisions:** ADR-007 (CQRS scope), ADR-021 (gap model), ADR-022 (selective read
strategy), ADR-011 Amendment 1 (registry schema home)

---

## 1. What Step 5 must overcome

Gap Analysis needs what a target model requires and what a learner has attained. It can obtain
neither today: every `api` package publishes identifiers only, and ADR-011 forbids reaching into
another context's schema. Step 5 therefore does three things — creates two published read contracts,
introduces durable event delivery for one of them, and builds the analytical core on top.

It also ends zero-touch, deliberately and as anticipated. Steps 3 and 4 proved **consumer
isolation**; Step 5 measures **additive integration cost**. The change to Competency Modelling must
be small, purely additive, and confined to a published contract — and it is to be measured and
reported, not merely asserted (ADR-022).

---

## 2. Exact `competency.api` additions

Two records and one event. Nothing else, and nothing removed.

```
competency/api/
├── CompetencyFrameworkId.java        (exists, unchanged)
├── CompetencyId.java                 (exists, unchanged)
├── CompetencyModelRegistered.java    NEW — integration event
└── CompetencyModelSnapshot.java      NEW — the compiled payload
```

```java
// The event. Emitted after a framework is registered and persisted.
public record CompetencyModelRegistered(
        CompetencyFrameworkId frameworkId,
        Instant registeredAt,
        CompetencyModelSnapshot model) { }

// The compiled model: flat, EMF-free, self-contained.
public record CompetencyModelSnapshot(
        String frameworkName,
        String frameworkVersion,
        List<Level> levels,            // code, ordinal
        List<Competency> competencies) {

    public record Level(String code, String name, int ordinal) { }

    // definedLevelCodes: the levels for which this competency has a descriptor — what the model
    // makes available, not what it demands. Identity is derived (ADR-019 Amendment 1).
    public record Competency(CompetencyId id, String code, String name,
                             String areaCode, List<String> definedLevelCodes) { }
}
```

> **Correction C1 (3 Aug 2026, Phase 1 planning).** This record originally carried a
> `requiredLevelCode` field. **It was removed: no such value exists and none can be derived.** The
> M2 metamodel (ADR-003) has no requirement concept — a `LevelDescriptor` states what a proficiency
> level *means* for a competency, never what level is demanded — so the field was an invention of
> the plan rather than a property of the model, and building against it would have produced a
> contract the producer could not honestly fill.
>
> **The governing principle, now recorded in ADR-021: gap analysis compares attainment against an
> *analysis target*, not against an intrinsic competency requirement.** The model supplies the
> available levels; `AnalyseGapCommand` supplies the target, defaulting to the highest level for
> which a competency has a descriptor when the request omits one. Each stored gap records the target
> it was measured against, because the same attainment yields a different gap under a different
> target.
>
> A second correction of the same kind: `CompetencyId` is **derived** from
> `(frameworkId, competencyCode)` per ADR-019 Amendment 1. Planning found that nothing in the system
> ever minted one — the metamodel identifies competencies by code alone — so learner references were
> not merely unvalidated but unmatchable, and gap computation could not have joined anything. The
> derived id is also exposed in `FrameworkDetailResponse.CompetencyResponse`, an additive REST field
> so that clients obtain real identifiers instead of inventing them. Adding a component to that
> response record is not a published-module-API signature change; the zero-breaking-signature
> criterion applies to `competency.api`, which gains only new types.

Three constraints shaped this. **No `EObject` crosses the boundary** — ADR-012 confines EMF to
Competency Modelling, so compilation happens *inside* that module, traversing the M1 graph through
the existing `CompetencyMetamodel` façade. **`publishedContractsArePure` permits only `java..`,
`shared..`, `..api..` and `org.springframework.modulith..`** in api packages, which these records
satisfy. **The area code travels as a plain string** rather than a typed identifier, because
`CompetencyArea` has no published identity and inventing one would widen the contract for no
consumer.

The name is `CompetencyModelRegistered`, not `ModelPublished`: registration is the trigger under
ADR-022, and naming the event for a lifecycle state that does not exist would be a lie in the
contract. When publication arrives, `CompetencyModelPublished` joins it and the projection filters.

**Producer change, measured:** one `ApplicationEventPublisher` field and one publish call in
`CompetencyFrameworkService.register`, plus a package-private compiler class in
`competency.application`. Target: **under 15 added lines in existing files, zero modified
signatures, zero deletions.** The actual figure is reported in the completion review as the RQ2
integration-cost measurement.

---

## 3. Exact `learner.api` additions

One query contract and two records — synchronous, per ADR-022.

```
learner/api/
├── LearnerId.java                 (exists, unchanged)
├── LearnerAttainmentQuery.java    NEW — published port
└── AttainedCompetency.java        NEW — result with provenance
```

```java
public interface LearnerAttainmentQuery {
    /** Empty when the learner has no profile — absence is an outcome, not an error (ADR-017). */
    List<AttainedCompetency> attainmentsFor(LearnerId learnerId);
}

public record AttainedCompetency(
        CompetencyId competencyId,
        CompetencyFrameworkId frameworkId,
        int attainedOrdinal,
        String attainedLevelCode,
        Instant resolvedAt,
        List<EvidenceSummary> evidence) {          // provenance — ADR-021's chain

    public record EvidenceSummary(String type, int claimedOrdinal, String claimedLevelCode,
                                  double confidence, String source, Instant recordedAt) { }
}
```

**Evidence provenance is carried, not summarised.** ADR-021's explainability chain runs
requirement → attainment → evidence, and it breaks at the module boundary unless the contract
carries it. `EvidenceType` is flattened to a `String` because the enum lives in `learner.domain.model`
and `publishedContractsArePure` forbids a domain type in an api package.

Implemented by an adapter in `learner.infrastructure.persistence` reading through the existing
repository — no new table, no change to the aggregate, and no change to the ownership rules, since
Gap Analysis asks for a learner it was already authorised to analyse.

---

## 4. Event shape and delivery

Durable publication via Spring Modulith, scoped to exactly one producer, one consumer, one event
type (ADR-022). New dependency: `spring-modulith-starter-jpa`.

- **Publication:** `CompetencyFrameworkService.register` publishes after the aggregate is saved,
  inside the existing transaction.
- **Consumption:** `@ApplicationModuleListener` in `gapanalysis.infrastructure.projection` —
  transactional and asynchronous, so a projection failure cannot roll back a registration.
- **Durability:** the publication registry records incomplete deliveries, so a listener failure is
  recoverable rather than silent.

No event bus, no broker, no republication API, no choreography between other contexts.

---

## 5. Migration ownership

| Range | Schema | Migration | Owner |
|---|---|---|---|
| V1–V99 | `common` | `V2__create_event_publication_registry.sql` | **NEW** — infrastructure |
| V100–V199 | `competency` | unchanged | Competency Modelling |
| V200–V299 | `learner` | unchanged | Learner Profiling |
| V400–V499 | `gap_analysis` | `V400__create_competency_projection.sql` (phase 2) | **NEW** — Gap Analysis |
| V400–V499 | `gap_analysis` | `V401__create_gap_report_tables.sql` (phase 4) | **NEW** — Gap Analysis |

`V2__` creates the Modulith publication registry in `common` under ADR-011 Amendment 1: it holds
framework-managed metadata no module could own, and placing it in a business schema would make one
context custodian of another's delivery state. Flyway's own `flyway_schema_history` is the existing
precedent.

The `gap_analysis` schema is built by **two** migrations, one per phase that needs it. `V400__`
creates the projection tables; `V401__` creates the gap tables (`gap_report`, `skill_gap`, plus
evidence provenance), where snapshots are stored as columns per ADR-021 since a report must remain
explicable after its inputs move. Foreign keys inside `gap_analysis` only; `competency_id`,
`framework_id` and `learner_id` are unkeyed identifier values (ADR-011, ADR-019).

> **Amendment A4 (4 Aug 2026, phase 2).** This section originally named a single
> `V400__create_gap_analysis_tables.sql` carrying both sets of tables. They are **split**, because
> the phases that need them are separate: shipping the gap tables in phase 2 would add a schema no
> code reads and no test exercises, and a migration is the one artefact that cannot be quietly
> revised once applied. Delivered in phase 2: `V400__create_competency_projection.sql`, creating
> `projected_framework`, `projected_level`, `projected_competency` and
> `projected_competency_level` — four tables rather than the two this section anticipated, because
> a framework's proficiency scale and a competency's defined levels are both collections and
> neither is derivable from the other.

---

## 6. Module dependency impact

**The DAG gains no edge.** `gapanalysis` already declares
`allowedDependencies = {"competency :: api", "learner :: api"}`; both dependencies are exercised
for the first time rather than newly permitted. `competency` and `learner` gain no dependency on
anything — a producer publishing an event depends on Spring, not on its consumer.

| Rule | Effect |
|---|---|
| `ModularityTests` | Unchanged; both edges pre-declared |
| `publishedContractsArePure` | Satisfied: new api records use `java..`, `shared..`, `..api..` only |
| `domainIsFrameworkFree` | Gap domain stays Spring-free; the listener is infrastructure |
| `applicationStaysOutOfAdapters` | Gap application depends on ports only, no `org.springframework.security..` |
| `restControllersOnlyInWebAdapters` | Controller in `gapanalysis.infrastructure.web` |
| `emfConfinedToCompetencyModule` | **Load-bearing here** — compilation to the snapshot happens inside competency; no EMF reaches `gapanalysis` |

All nine architecture tests are expected to pass **unmodified**.

---

## 7. Implementation sequence

| Phase | Content | Gate |
|---|---|---|
| **1 — Contracts + event infrastructure** | `competency.api` event and snapshot; the in-module compiler; `learner.api` query and its adapter; `spring-modulith-starter-jpa`; `V2__` registry. | Green; event round-trip asserted with `PublishedEvents`; **producer diff measured** |
| **2 — Projection** | `V400__` projection tables (A4: projection only; gap tables move to `V401__` in phase 4); `@ApplicationModuleListener`; projection repository and adapter. | **Delivered**: green; **+16 (184 actual)**; registering a framework populates the projection end to end, with delivery proven to travel the durable registry |
| **3 — Domain** | `GapReport`, `SkillGap`, snapshots, `GapSeverityPolicy` + default. Framework-free. | Green; severity substitutable via a lambda |
| **4 — Application + persistence** | `GapAnalysisService`, `AnalyseGapCommand`, gap tables and adapter, ownership per ADR-015 A1/ADR-016. | Green; ownership matrix with no security infrastructure |
| **5 — Web adapter** | Controller, mapper, advice, DTOs; `SecurityConfig` gap rules. | Green; role/ownership matrix with real tokens; 404 non-disclosure |
| **6 — Documentation & evidence** | Module diagram; evidence pack incl. the integration-cost measurement; completion review; ADR-019 closure note. | DoD met; step report; stop |

Phase 1 is deliberately first and deliberately small: it is the only phase that touches another
module, and isolating it makes the integration cost measurable rather than entangled with the
analytical core.

---

## 8. Definition of Done (draft)

`mvn verify` BUILD SUCCESS with ≈180+ tests, zero failures on real PostgreSQL; an event published
by Competency Modelling demonstrably populates the Gap Analysis projection; gap computation
produces explainable findings carrying requirement, attainment and evidence provenance; the
ownership matrix and 404 non-disclosure proven with real tokens; nine architecture tests pass
unmodified; the producer-side integration cost measured and reported; documentation references
check clean; ADR-019 closure note recorded; step report delivered; stop-and-wait observed.

---

## 9. Risks

| Risk | L/I | Mitigation |
|---|---|---|
| Async listener makes tests flaky | Med/Med | Modulith `Scenario` support; assert on published events plus an explicit end-to-end projection test |
| Registry table interacts badly with `ddl-auto: validate` | Med/Low | Registry schema supplied by Modulith's own DDL, applied via Flyway in `common`; entity mapping not ours to declare |
| Snapshot contract grows to mirror the whole metamodel | Med/Med | Contract carries only what gap computation consumes; additions require a recorded reason |
| Projection and source diverge silently | Low/Med | Projection is derived and rebuildable; divergence is a defect to repair by rebuild, not by migration (ADR-007) |
| Integration cost creeps beyond the additive target | Med/High | Phase 1 isolated and measured; exceeding the target is a finding to report, not a number to quietly revise |
| ADR-007 reconstruction diverges from the design log | Low/High | Status records the outstanding comparison; 019/021/022 cite it, so a correction propagates deliberately |
