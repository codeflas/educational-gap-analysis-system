# ADR-011: Persistence isolation — one PostgreSQL schema per module

Status: Accepted
Date: 2026-08-02

## Problem
A modular monolith whose modules share one undifferentiated database schema erodes exactly the
boundary the architecture promises: cross-module joins and foreign keys are one lazy query away,
and module extraction becomes a data archaeology project. RQ2's coupling evidence must hold at
the persistence tier, not only in Java packages.

## Alternatives
1. Single shared schema (status quo ante) — simplest; invites silent coupling.
2. Schema per module, one database — logical isolation, one operational unit.
3. Database per module — strongest isolation; operationally disproportionate for a monolith and
   destroys single-transaction simplicity within the deployable.

## Decision
Option 2. One PostgreSQL schema per application module (competency, learner, catalogue,
gap_analysis, recommendation), created by a common baseline migration. Cross-schema foreign
keys are prohibited; cross-context references are identifier values only. Flyway migrations are
namespaced per module to prevent version collisions: common V1-V99, competency V100-V199,
learner V200-V299, catalogue V300-V399, gap_analysis V400-V499, recommendation V500-V599.

## Consequences
Module extraction to a service is a data migration, not a redesign. The Modulith topology is
mirrored where it is usually first violated. Migration ownership is unambiguous per module.

## Trade-offs
No database-enforced referential integrity ACROSS contexts (within a context it remains
mandatory); cross-context consistency becomes an application/event concern — accepted, because
that is precisely the consistency model a future distributed deployment would impose anyway.

## Quality attributes affected
Modifiability (+), evolvability/extractability (+), integrity across contexts (- managed),
performance (neutral; no cross-schema joins were intended).

## Amendment 1 — framework-managed infrastructure metadata belongs to `common` (Step 5, Accepted 2026-08-03)

Step 5 introduces durable Spring Modulith event publication, whose registry table records which
listener has consumed which event. That table has no owner under the rule above: it belongs to no
context, it is written by the framework rather than by any module's code, and placing it in a
business schema would make one context's schema the custodian of another's delivery state — the
precise coupling this ADR exists to prevent.

**Decision.** Framework-managed infrastructure metadata lives in the `common` schema, created by the
common migration range (V1–V99). Business data remains module-owned without exception.

The distinguishing test is **who owns the rows**. A module's schema holds data that module's domain
is responsible for and could take with it if extracted. The publication registry holds neither: no
domain object corresponds to a row, no module could meaningfully claim it, and on extraction each
service would simply create its own. Flyway's own `flyway_schema_history` is the existing precedent
for infrastructure state that sits outside the module map, and this amendment states the principle
that case was already following.

The prohibition on cross-schema foreign keys is unchanged and unaffected: the registry references
nothing, and nothing references it.

**Scope limit.** This admits infrastructure metadata into `common`; it does not admit shared
business tables, and it is not a licence to place anything convenient there. A table qualifies only
if no module could own it — which is a narrow test, and deliberately so.

## Future evolution
Per-module database credentials with schema-scoped grants would upgrade the convention to a
hard guarantee; noted as hardening beyond dissertation scope. Should a second piece of
framework-managed metadata appear, it joins the registry in `common` under Amendment 1's test
rather than prompting a new decision.
