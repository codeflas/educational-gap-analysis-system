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

## Future evolution
Per-module database credentials with schema-scoped grants would upgrade the convention to a
hard guarantee; noted as hardening beyond dissertation scope.
