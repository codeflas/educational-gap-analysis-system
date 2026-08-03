# EGAS — Educational Gap Analysis System

MSc Software Engineering dissertation prototype (University of Limerick).
Model-driven educational gap analysis with AI-based recommendation support.

**Stack.** Java 21 · Spring Boot 3.4 · Spring Modulith · PostgreSQL 16 · Flyway ·
EMF/Ecore (from Step 2) · React + TypeScript (separate app, from W4) · Docker · GitHub Actions.

## Architecture at a glance

Single deployable; five bounded contexts plus a minimal shared kernel. Boundaries are
machine-enforced: Spring Modulith verifies the inter-module topology, ArchUnit verifies
ports-and-adapters layering inside each module. Both run as ordinary tests in `mvn verify`.

```
ie.ul.egas
├── shared            shared kernel (Identifier only — deliberately tiny)
├── platform          cross-cutting configuration (security baseline)
├── competency        Competency Modelling — Ecore M2, model (M1) lifecycle, validation
├── learner           Learner Profiling — evidence → proficiency resolution
├── catalogue         Learning Catalogue — resources mapped to competencies
├── gapanalysis       Gap Analysis (core domain) — typed gaps, compiled read projection
└── recommendation    Recommendation — pathway synthesis, strategies, explanations
```

Module → schema map: `competency`, `learner`, `catalogue`, `gap_analysis`, `recommendation`
(one PostgreSQL schema per module; no cross-schema foreign keys — see ADR-011).

## Running locally

```bash
docker compose up -d        # or rely on spring-boot-docker-compose during `spring-boot:run`
mvn spring-boot:run
mvn verify                  # tests + architectural fitness functions
```

First checkout: `mvn wrapper:wrapper` once, then commit the wrapper (CI uses plain `mvn`,
so this is convenience, not a gate). Requires Docker for Testcontainers-based tests.

## Architecture verification & documentation

- `ModularityTests` — Modulith `verify()` (cycles, internal access, undeclared deps) and
  generated PlantUML/module canvases in `target/spring-modulith-docs` (CI artifact).
- `HexagonalArchitectureTests` — framework-free domain, adapter-blind application layer,
  controllers confined to web adapters, pure `api` contracts.

## Decision log

`docs/adr` — extended MADR template and index (ADR-001…011).
