\# EGAS Architecture Review — Step 2 Completion



\*\*Project:\*\* Educational Gap Analysis System (EGAS) — Architectural Design of a Model-Driven Educational Gap Analysis System with AI-Based Recommendation Support

\*\*Student:\*\* Shubham Digamber Biradar (25322206) · \*\*Supervisor:\*\* Dr. Salim Saay · University of Limerick, M.Sc. Software Engineering

\*\*Review date:\*\* 3 August 2026

\*\*Baseline under review:\*\* Step 2 Git baseline — Competency Modelling vertical slice

\*\*Verified build evidence:\*\* `mvn verify` → BUILD SUCCESS · Tests run: 50 · Failures: 0 · Errors: 0 · executed against PostgreSQL 16 via Docker/Testcontainers with Flyway migrations applied



\---



\## 1. Executive Summary



Step 2 delivered the first complete vertical slice of EGAS: the Competency Modelling model registry, spanning Ecore metamodel (M2), text-to-model injection, conformance validation, an invariant-enforcing aggregate, JSONB persistence of interpreted M1 models, a secured REST API with RFC 9457 error semantics, and a fifty-test verification suite executed against real infrastructure.



The step's significance for the dissertation is that the model-driven engineering claims moved from architecture documents into executable, tested code. Models-at-runtime is no longer a design intention: a dynamic M2 exists as the single source of truth, M1 instances are interpreted rather than compiled, conformance is checked mechanically against the metamodel plus bespoke well-formedness rules, and the system's first first-class transformation (command → model injection) is in production code with tests. Simultaneously, every architectural promise made in Step 1 was preserved and, where new territory was entered (EMF's position relative to hexagonal purity), the tension was resolved by explicit decision (ADR-012) and encoded as executable fitness functions rather than convention.



\*\*Satisfaction of original architectural goals:\*\* achieved. The module dependency DAG gained zero new edges; the domain ring remains framework-free (with EMF's admission scoped and machine-enforced); security remained deny-by-default; the performance posture was actively protected (column-only listing path); and every significant decision is traceable to an ADR and, where enforceable, to a fitness function.



\*\*Readiness to proceed:\*\* \*\*GREEN.\*\* Step 2 meets its Definition of Done (§8). Ten concerns are recorded (§2.12), none blocking; one (C-10) is a forward-looking design point that Step 3 must resolve on entry.



\---



\## 2. Architecture Compliance Review



\### 2.1 Domain-Driven Design

The slice exhibits the full DDD toolkit applied with restraint. `CompetencyFramework` is a genuine aggregate root: its central invariant — \*a framework that does not conform to the metamodel cannot exist\* — is enforced inside the boundary via the `register(...)` factory (always-valid aggregate), with the validator supplied through the double-dispatch idiom so the aggregate performs no service lookup. Value objects (`FrameworkName`, `FrameworkVersion`, `FrameworkDescriptor`, `FrameworkSummary`) validate and normalise at construction and carry equality by value. Domain exceptions express business outcomes (`DuplicateFrameworkException`, `FrameworkNotFoundException`, `ModelConformanceException`), not infrastructure failures. The repository is a domain-owned port. Ubiquitous language (framework, competency, proficiency level, conformance, registration) is consistent from metamodel to REST contract. The distinctive DDD move — treating the EMF `EObject` graph itself as aggregate state — is the deliberate consequence of ADR-012 and is what makes the MDE claims real rather than decorative.



\### 2.2 Hexagonal / Clean Architecture

The ports-and-adapters discipline holds and is machine-verified. Dependencies point inwards only; the domain knows neither Spring, nor JPA, nor Jackson, nor serialisation machinery. The driven port (`CompetencyFrameworkRepository`) is implemented by a package-private JPA adapter; the driving side enters through a thin controller. Composition of the domain validator happens at the application boundary (`CompetencyModuleConfiguration`), which is precisely what allows the domain ring to remain annotation-free. The deliberate deviation — concrete application services without driving-port interfaces — is a recorded YAGNI ruling: substitution is real on the driven side (JPA today, alternatives and mocks tomorrow) and speculative on the driving side. The W10 importer will test this ruling (§7).



\### 2.3 SOLID

\*SRP:\* each class carries one reason to change (metamodel definition, injection, validation, orchestration, mapping, persistence translation are all separated). \*OCP:\* new conformance rules extend the validator without touching callers; structural rules extend by editing M2, not code. \*LSP:\* trivially satisfied; the one interface hierarchy (`ConformanceValidator`) has behaviourally substitutable implementations, demonstrated by lambda stubs in tests. \*ISP:\* the repository port exposes four cohesive operations; no fat interfaces. \*DIP:\* the aggregate depends on the validator abstraction; the application layer depends on the repository abstraction; both are bound at composition time.



\### 2.4 Low Coupling / High Cohesion

The headline metric: \*\*zero new inter-module dependencies.\*\* Spring Modulith's `verify()` passes with the locked `allowedDependencies` DAG unchanged — a full vertical slice was added without any other bounded context learning of its existence (the only files touched outside the module were platform composition and shared configuration, which is the composition root's job). Intra-module, efferent coupling of the domain ring is limited to the JDK, the module's own `api` identifiers, the shared kernel, and EMF core. This is direct, citable RQ2 evidence and establishes the baseline for the longitudinal coupling measurements planned for W11.



\### 2.5 Package Boundaries

Adapter internals (`JpaCompetencyFrameworkRepository`, `FrameworkModelSpringDataRepository`, `FrameworkModelJpaEntity`, `EmfJsonModelSerializer`, controller, mapper, advice) are package-private: other layers of the same module cannot even name them. The public surface is the minimum viable: domain model, ports, application services, and REST DTOs (public out of transport necessity, and deliberately located in `infrastructure.web.dto`, never in the module's `api` package — the module contract and the REST contract are distinct artefacts with distinct consumers).



\### 2.6 Dependency Direction

Enforced, not asserted: `domainDoesNotReachOutwards`, `applicationStaysOutOfAdapters`, `domainIsFrameworkFree`, and `publishedContractsArePure` all execute on every build, now against populated selections (the Step 1 `failOnEmptyShould` concession was retired this step, restoring typo protection).



\### 2.7 Modularity

The slice demonstrates the modularity thesis operationally: hexagonal layering \*inside\* the module, Modulith boundaries \*between\* modules, schema isolation \*beneath\* them (ADR-011, now implemented with migration version ranges). The Modulith Documenter output regenerated with an unchanged topology — the correct result for a step that was supposed to add depth, not breadth.



\### 2.8 Maintainability

Three mechanisms dominate. The metamodel façade confines dynamic EMF's stringly API to one construction site, giving every consumer a compile-checked vocabulary — this is also what will make the W3 freeze migration mechanical rather than invasive. Hand-written mappers keep transformations explicit and debuggable. ADR traceability (decision → rationale → enforcing rule) means the "why" survives contact with future maintainers, including the dissertation's examiners.



\### 2.9 Security-by-Design

The deny-by-default baseline survived its first real endpoints intact. Business endpoints require authentication; integration tests authenticate through `spring-security-test` rather than weakening the chain; the single new permit (OpenAPI documentation paths) is scoped, justified in code comments, and reversible per profile. Secrets remain environment-injected; sessions remain stateless; CSRF rationale remains valid (no cookie state). Role-differentiated authorisation is absent by plan, not by drift — it is Step 3's charter (ADR-010).



\### 2.10 Testability

Design-for-test is visible in the seams: the validator interface admits lambda stubs (the aggregate invariant is tested without EMF fixtures), the injected `Clock` makes registration timestamps deterministic, package co-location lets integration tests exercise package-private adapters without visibility widening, and Testcontainers guarantees dialect fidelity (real PostgreSQL 16, real Flyway, real jsonb).



\### 2.11 Performance Considerations

Actively managed, not deferred wholesale: the listing path is a column-only interface projection (jsonb never fetched, model never deserialised); `open-in-view` is off; per-operation `ResourceSet`s eliminate shared mutable EMF state under virtual-thread concurrency at the cost of a small allocation; `saveAndFlush` deliberately surfaces the uniqueness constraint at a deterministic point. The detail endpoint deserialises the full model per request — acceptable at dissertation scale and flagged for the W11 measurement pass (C-05). A jsonb GIN index is documented as premature until jsonb predicates are actually queried.



\### 2.12 Concerns Register



| ID | Concern | Severity | Disposition |

|----|---------|----------|-------------|

| C-01 | `modelRoot()` exposes the mutable EObject graph; immutability is by documented contract, not type system | Low | Accepted; serialiser copies before attach; read-only EMF adapters recorded as hardening option |

| C-02 | `FrameworkSource` Java enum duplicates the M2 `FrameworkSourceKind` EEnum | Low | Accepted for the dynamic-EMF phase; superseded by the generated enum at the W3 freeze |

| C-03 | Metadata exists in both relational columns and the model; consistency is guaranteed only at construction | Low–Med | Safe while frameworks are immutable post-registration; must be re-examined when the publish transition and any mutation paths arrive (W6) |

| C-04 | `reconstitute(...)` trusts the store (no revalidation on load) | Low | Accepted trade-off, documented; defensive revalidation remains a switchable option |

| C-05 | Detail endpoint deserialises the entire model per request | Low | Acceptable at scale; profile in W11 before considering caching or read projection |

| C-06 | External version pins (EMF release train, springdoc) carry upgrade coupling | Low | Resolved green in the verified build; align the EMF artefacts as a train on any upgrade |

| C-07 | No pagination on the listing endpoint | Low | Documented extension; port signature change would be additive |

| C-08 | Dynamic `EPackage` cannot be frozen; immutability is by discipline | Low | Disappears at the freeze (generated packages are frozen) |

| C-09 | API integration tests share one Spring context and database; isolation relies on unique framework names | Low–Med | Adequate at 8 tests; adopt an explicit cleanup or data-namespace strategy if the suite grows or flakes |

| C-10 | The `restControllersOnlyInWebAdapters` rule constrains where a Step 3 token-issuance controller may live | Med (forward-looking) | Resolve on Step 3 entry: place platform web adapters under `platform.infrastructure.web` so the rule holds unmodified (§9) |



No concern is assessed as blocking; none warrants deviation from the frozen architecture.



\---



\## 3. Implementation Summary



\*\*Domain model.\*\* A complete DDD core for the Competency Modelling context: the `CompetencyFramework` aggregate wrapping typed metadata plus the M1 model graph; six value objects; three domain exceptions; the repository port; and the conformance validation contract with its EMF-backed reference implementation.



\*\*EMF metamodel (M2).\*\* `CompetencyMetamodel` defines the competency metamodel v1 programmatically (dynamic Ecore per ADR-003): five EClasses (`CompetencyFramework`, `ProficiencyLevel`, `CompetencyArea`, `Competency`, `LevelDescriptor`), one EEnum (`FrameworkSourceKind`), containment hierarchies, and the non-containment `prerequisites` cross-reference that forms the pathway graph. A typed façade centralises all feature access. The design keeps frameworks without proficiency scales representable (levels 0..\\\*) — a deliberate framework-independence property for RQ1.



\*\*Validation.\*\* Two tiers: Bean Validation on DTOs for transport shape (→ 400), and conformance for model semantics (→ 422). Conformance itself splits into structural checks driven mechanically from M2 by EMF's `Diagnostician` and bespoke invariants standing in for OCL: blank mandatory text, level/area/competency code uniqueness, foreign-reference containment, self-prerequisite prohibition, and prerequisite acyclicity via three-colour depth-first search with cycle-path reporting. Injection-time resolution failures (`UNRESOLVED\_PREREQUISITE`, `UNKNOWN\_LEVEL`) are rejected before the aggregate is invoked. Fourteen stable violation codes form the machine-readable error contract.



\*\*Serialisation.\*\* `EmfJsonModelSerializer` bridges EMF and jsonb via emfjson-jackson: per-operation `ResourceSet`s (thread safety under Loom), deep copy before Resource attachment (the aggregate's live graph is never mutated), package-registry-based `eClass` resolution, and intra-resource fragment paths for cross-references.



\*\*Persistence layer and repository implementation.\*\* An adapter-private JPA entity maps to `competency.framework\_model` (metadata columns + jsonb content); a package-private Spring Data repository supplies derived queries and a column-only interface projection for summaries; `JpaCompetencyFrameworkRepository` implements the domain port, mapping aggregate ↔ entity and translating the unique-constraint violation into the domain's `DuplicateFrameworkException` (closing the check-then-act race behind the service's fast-path check).



\*\*Database integration and Flyway.\*\* Migration `V100\_\_create\_framework\_model\_table.sql` under the newly ratified per-module version ranges (ADR-011: competency owns V100–V199), schema-qualified, with the `(name, version)` unique constraint. Hibernate is demoted to `ddl-auto: validate`, making Flyway the sole schema owner and converting mapping drift into startup failure.



\*\*REST API.\*\* `POST /api/frameworks` (201 + Location), `GET /api/frameworks` (summaries, content never loaded), `GET /api/frameworks/{id}` (full rendered model tree). RFC 9457 problem details throughout, with the conformance report's violations attached as an extension member on 422. OpenAPI annotations plus Swagger UI (documentation paths permitted with recorded rationale; all business endpoints authenticated).



\*\*Testcontainers and testing infrastructure.\*\* All database-touching tests run against genuine PostgreSQL 16 through the shared `TestcontainersConfiguration` (made public this step for cross-package import). A shared `FrameworkFixtures` builder supplies one canonical valid framework and targeted invalid variants. `MetamodelEvidenceTests` emits the citable `.ecore` artefact each build.



\---



\## 4. Files and Components



\### 4.1 New packages



| Package | Responsibility |

|---|---|

| `competency.domain.metamodel` | M2 definition and typed access façade |

| `competency.domain.model` | Aggregate, value objects, domain exceptions |

| `competency.domain.validation` | Conformance contract, report model, EMF-backed validator |

| `competency.application` | Use-case orchestration, command model, T2M injection, module composition |

| `competency.infrastructure.persistence` | JPA mapping, Spring Data internals, port adapter, emfjson bridge |

| `competency.infrastructure.web` (+ `.dto`) | REST controller, DTOs, mapping, RFC 9457 error rendering |

| `platform.config` | Cross-cutting beans: `Clock`, OpenAPI document metadata |



\### 4.2 New classes (responsibility per class)



| Layer | Class | Responsibility |

|---|---|---|

| Domain / metamodel | `CompetencyMetamodel` | Builds M2 once; exposes compile-checked EClass/feature accessors; factory + enum-literal resolution |

| Domain / model | `CompetencyFramework` | Aggregate root; enforces conformance invariant at `register`; identity equality |

| | `FrameworkDescriptor` | Typed metadata VO; blank-description normalisation |

| | `FrameworkName`, `FrameworkVersion` | Validating VOs (trim/length; permissive real-world version pattern) |

| | `FrameworkSource`, `ModelStatus` | Source-of-origin enum (mirrors EEnum); lifecycle enum (DRAFT/PUBLISHED) |

| | `FrameworkSummary` | Content-free read model for listings |

| | `DuplicateFrameworkException`, `FrameworkNotFoundException` | Business-outcome exceptions |

| Domain / port | `CompetencyFrameworkRepository` | Technology-blind persistence contract incl. summary path |

| Domain / validation | `ConformanceValidator` | Functional domain-service contract (DIP + test seam) |

| | `EmfConformanceValidator` | Diagnostician structural pass + bespoke invariants incl. cycle detection |

| | `ConformanceReport`, `ConformanceViolation` | Violation model with stable codes and severity |

| | `ModelConformanceException` | Carries the full report to the error-rendering boundary |

| Application | `RegisterFrameworkCommand` | Framework-free use-case input with null-normalised nesting |

| | `FrameworkModelAssembler` | T2M injection; two-pass reference resolution; injection-time conformance |

| | `CompetencyFrameworkService` | Transaction boundary; orchestration; fast-path duplicate check |

| | `CompetencyModuleConfiguration` | Instantiates the domain validator as a bean (keeps domain annotation-free) |

| Persistence | `FrameworkModelJpaEntity` | Adapter-private mapping record; jsonb via `@JdbcTypeCode(SqlTypes.JSON)` |

| | `FrameworkModelSpringDataRepository` | Derived queries; `FrameworkSummaryView` column-only projection |

| | `JpaCompetencyFrameworkRepository` | Port adapter; aggregate↔entity mapping; constraint→domain-exception translation |

| | `EmfJsonModelSerializer` | Model↔jsonb bridge; per-op ResourceSets; copy-before-attach |

| | `ModelSerializationException` | Infrastructure failure signal (→ 500) |

| Web | `CompetencyFrameworkController` | Thin HTTP adapter; 201 + Location; OpenAPI annotations |

| | `FrameworkWebMapper` | DTO↔command; EObject→response projection via façade |

| | `CompetencyFrameworkExceptionHandler` | Module-local RFC 9457 mapping (422 + violations, 409, 404) |

| | `RegisterFrameworkRequest`, `FrameworkSummaryResponse`, `FrameworkDetailResponse` | Transport contracts; tier-1 validation annotations |

| Platform | `TimeConfiguration` | Injectable UTC `Clock` for deterministic time |

| | `OpenApiConfiguration` | API document metadata (ADR-009) |



\### 4.3 Important configuration files (modified)



| File | Step 2 change |

|---|---|

| `pom.xml` | + data-jpa, validation, EMF runtime (ecore/common/xmi), emfjson-jackson, springdoc 2.7.0 |

| `application.yml` | + `jpa.open-in-view: false`, `jpa.hibernate.ddl-auto: validate`, `mvc.problemdetails.enabled: true` |

| `platform/security/SecurityConfig.java` | + permitted OpenAPI documentation paths (rationale in code); deny-by-default unchanged |

| `HexagonalArchitectureTests.java` | + two ADR-012 EMF-confinement rules; javadoc updated |

| `TestcontainersConfiguration.java` | visibility → public (cross-package `@Import`) |

| `src/test/resources/archunit.properties` | \*\*deleted\*\* — empty-selection protection restored |



\### 4.4 Database migrations



| Migration | Content |

|---|---|

| `db/migration/competency/V100\_\_create\_framework\_model\_table.sql` | `competency.framework\_model`: uuid PK, metadata columns, `content jsonb not null`, `registered\_at timestamptz`, unique `(name, version)` |



\### 4.5 Key architectural components

The four load-bearing elements of the step, in causal order: the \*\*metamodel façade\*\* (single source of M2 truth and the freeze-migration pivot), the \*\*assembler\*\* (the first first-class transformation), the \*\*conformance validator\*\* (the RQ1 mechanism made executable), and the \*\*port adapter + serialiser pair\*\* (models-at-runtime persisted without leaking EMF outward).



\---



\## 5. Testing Review



Fifty tests, four confidence tiers. Distribution (Step 2 classes exact; Step 1 foundation contributes the remaining five — module verification/documentation and application smoke):



| Category | Classes | Tests | Confidence provided |

|---|---|---|---|

| Unit — MDE core | `CompetencyMetamodelTests` (4), `FrameworkModelAssemblerTests` (4), `EmfConformanceValidatorTests` (8), `FrameworkValueObjectTests` (3), `CompetencyFrameworkAggregateTests` (3), `MetamodelEvidenceTests` (1) | 23 | The metamodel has the intended shape and cannot drift silently; injection builds correct graphs and rejects unresolvable references; every conformance rule fires on its trigger and stays silent on valid input; VO validation and normalisation hold; the aggregate invariant is proven in isolation via stubbed validators (no EMF needed to test the \*rule\*) |

| Unit — serialisation | `EmfJsonModelSerializerTests` | 3 | Round-trips are structurally equal (`EcoreUtil.equals`), cross-references resolve inside the restored resource, and serialisation provably does not mutate the aggregate's live graph (copy-before-attach verified) |

| Integration — persistence \& API | `JpaCompetencyFrameworkRepositoryTests` (4), `CompetencyFrameworkApiTests` (8) | 12 | Against real PostgreSQL 16: Flyway schema validity (via `ddl-auto: validate` at context start), jsonb round-trip fidelity, column-only summaries, constraint→domain-exception translation; and the full HTTP contract — 201+Location, rendered model tree, 422 with `CYCLIC\_PREREQUISITES` in the violations extension, 409, 404, 400, and the 401 security baseline |

| Architecture | `HexagonalArchitectureTests` (7 rules), `ModularityTests` + smoke (Step 1) | 12 | Structural conformance is re-proven on every build: layer purity, dependency direction, controller placement, contract purity, and both ADR-012 EMF-confinement rules; Modulith `verify()` certifies the unchanged module DAG |



\*\*What this suite does \*not\* yet cover (honest gaps):\*\* true concurrent duplicate registration (the sequential constraint-translation path is covered; a genuine race is not exercised), performance characteristics (deferred to W11 by plan), and mutation-testing of the validator logic (a possible W11 rigour add-on). None gates Step 2.



\---



\## 6. Dissertation Evidence



\*\*Generated artefacts (already produced per build):\*\*

\- `target/dissertation/competency-metamodel-v1.ecore` — the citable M2 artefact and future genmodel input (Design chapter, RQ1).

\- `target/spring-modulith-docs/\*` — generated module diagrams proving the unchanged DAG (RQ2 coupling evidence).

\- ADR-011 and ADR-012 full texts — quotable decision records with alternatives and trade-offs (Bass-style rationale).

\- The two ArchUnit EMF rules — reproduce as a code listing: \*executable architecture\* is a strong dissertation motif ("the boundary claim is a test, not a diagram").



\*\*Figures to render from checked-in sources:\*\* `competency-metamodel.puml` (the central M2 class diagram) and `competency-module-internal.puml` (ports-and-adapters realisation of one context).



\*\*Screenshots to capture now (state-dependent — capture before the codebase moves on):\*\*

1\. `mvn verify` tail: BUILD SUCCESS, Tests run: 50, Failures: 0, Errors: 0.

2\. The green GitHub Actions run for the Step 2 baseline commit.

3\. Swagger UI endpoint list with the documented 201/400/409/422 responses.

4\. `psql`: `\\dn` (five module schemas) and `\\d competency.framework\_model` (jsonb column, unique constraint).

5\. A stored row's `content` pretty-printed — emfjson at rest, with `eClass` URIs and fragment-path cross-references visible. This single screenshot evidences the entire ADR-005 persistence strategy.



\*\*Tables ready for inclusion:\*\* the ADR index (001–012 with statuses); the Flyway version-range allocation; the 14-code conformance violation catalogue (code → meaning → detection point: injection vs model-level — an excellent table for the RQ1 section); the test distribution table above; the endpoint/status-code contract.



\*\*Metrics:\*\* record a \*\*coupling baseline now\*\* (e.g. JDepend or jQAssistant efferent/afferent counts per module and per ring) and repeat at each subsequent step — a longitudinal series is far stronger RQ2 evidence than a single end-state snapshot. Also worth logging per step: test count, class count per ring, and lines of migration SQL (trivial to collect, useful for the evaluation chapter's scale narrative).



\*\*Figures to produce later:\*\* a registration sequence diagram (POST → assembler → validator → aggregate → adapter → jsonb); an M1 object diagram of the sample framework; the system-level C4 container view once React lands (\~W4); the security view after Step 3.



\---



\## 7. Technical Debt



\*\*Deferred by plan (roadmap, not debt):\*\* publish transition + `ModelPublished` event + Gap Analysis projection (W6, ADR-007); M2 freeze and genmodel (end W3 — a \*deadline\*, see risks); React front end (\~W4); evaluation instrumentation (W11).



\*\*Acceptable shortcuts, recorded:\*\* concerns C-01…C-08 in §2.12 — mutable EObject exposure by contract, enum duplication until freeze, metadata dual representation, trust-the-store reconstitution, per-request detail deserialisation, external version pins, unpaginated listing, discipline-based EPackage immutability. Each has a documented rationale and a revisit trigger.



\*\*Future refactoring opportunities (non-urgent, architecture-conforming):\*\* stable problem-type URIs replacing `about:blank` (a small error-catalogue improvement); a production profile that disables springdoc; per-module database credentials upgrading ADR-011 from convention to guarantee; Spring Modulith observability/event-registry starters when eventing lands (W6); optional defensive revalidation toggle on load; an explicit API-test data-cleanup strategy if C-09 materialises.



\*\*Risks to monitor in later steps:\*\*

1\. \*\*Freeze-window risk (highest):\*\* the dynamic→generated EMF migration at end of W3 is the largest planned refactor. Mitigation already in place: every metamodel access flows through the façade, so the change is mechanical (swap façade internals for generated literals) — but it must not slip, or dynamic-EMF idioms will accrete.

2\. \*\*Metadata dual-write discipline (C-03):\*\* the moment any mutation path exists (publish, future edits), construction-time consistency stops being a complete argument; introduce a single write path or an invariant check then.

3\. \*\*EMF/springdoc train alignment (C-06)\*\* on any dependency bump.

4\. \*\*API-test isolation (C-09)\*\* as the endpoint surface multiplies in Steps 4–9.

5\. \*\*Driving-port ruling under pressure:\*\* the W10 importer should first attempt reuse of `CompetencyFrameworkService`; introduce driving-port interfaces only if it genuinely needs different transaction or error semantics — resist pre-emptive abstraction.



No item on this list recommends violating the frozen architecture; C-10's resolution (§9) is explicitly designed to keep the existing fitness function intact.



\---



\## 8. Definition of Done — Step 2



| # | Criterion | Status |

|---|---|---|

| 1 | Single vertical slice, no skipped-ahead functionality (rule 1) | ☑ Registry slice only; publish/eventing deliberately excluded |

| 2 | Compiles successfully (rule 2) | ☑ BUILD SUCCESS (user-verified, CI green) |

| 3 | All tests pass (rule 3) | ☑ 50 run / 0 failures / 0 errors on real PostgreSQL |

| 4 | Architectural decisions explained before code (rule 4) | ☑ Four rulings + per-layer rationale delivered pre-implementation |

| 5 | ADRs created/updated (rule 5) | ☑ ADR-011 → Accepted (+ ranges); ADR-012 created Accepted; index updated |

| 6 | Production-quality, no placeholders (rule 6) | ☑ No TODOs; documented trade-offs instead of stubs |

| 7 | SOLID / Clean / DDD / Hexagonal upheld (rule 7) | ☑ §2.1–2.3; machine-enforced where enforceable |

| 8 | Low coupling / high cohesion (rule 8) | ☑ Zero new module edges; Modulith verify green |

| 9 | Unit + integration + architecture tests per feature (rule 9) | ☑ 23 / 15 / 12 across the four tiers (§5) |

| 10 | Step report delivered (rule 10) | ☑ Seven-part report + full file walkthrough + this review |

| 11 | Stopped for approval before next step (rule 11) | ☑ Step 3 not started; §9 is planning only |

| G1–G5 | Five gates: conformance, SOLID, coupling, security, performance | ☑ All addressed with evidence (§2) |

| — | Baseline committed and pushed to Git | ☑ User-confirmed Step 2 baseline |



\*\*Verdict: Step 2 is DONE.\*\*



\---



\## 9. Step 3 Planning — Authentication \& Authorisation (ADR-010)



\*Planning only; no implementation is authorised by this section.\*



\### 9.1 Objectives

Implement the frozen ADR-010 security architecture: locally issued RSA-signed JWTs via Spring Security's OAuth2 Resource Server; a token-issuance endpoint for the dissertation's single-tenant context (statically configured principals — no user-management subsystem); the role model \*\*EDUCATOR / LEARNER / ADMIN\*\*; role-differentiated authorisation on the framework registry (writes restricted to EDUCATOR/ADMIN, reads to any authenticated principal); a bearer-aware authentication entry point (401 with `WWW-Authenticate: Bearer`, correctly distinguished from 403); and Swagger UI integration (bearer security scheme) so the API becomes interactively usable end-to-end.



\### 9.2 Why Step 3 logically follows Step 2

Step 2 created the system's first protected resources; Step 3 supplies the authorisation semantics they currently lack. Sequencing it now — before Steps 4–9 multiply the endpoint surface across four more contexts — means the cross-cutting security model is established once and inherited everywhere, rather than retrofitted five times. It also unblocks two dependencies: interactive Swagger usage (currently 401-bound by design), and Learner Profiling (Step 4), which requires an authenticated learner identity as its anchor.



\### 9.3 Architectural scope

Confined to the \*\*platform\*\* module plus authorisation rules; \*\*zero changes to competency domain or application code\*\* (this zero-touch property is itself an acceptance criterion — live modularity evidence). One design point must be resolved on entry (C-10): the token-issuance controller will live under `platform.infrastructure.web`, satisfying the existing `restControllersOnlyInWebAdapters` rule \*unmodified\* and giving the platform module the same hexagonal internal shape as business modules. Key material: environment-injected RSA keys with a dev-profile generated fallback; `JwtEncoder`/`JwtDecoder` beans in `platform.security`.



\### 9.4 Expected deliverables

Token endpoint (`POST /auth/token` or equivalent) issuing RS256 JWTs with a roles claim; resource-server configuration replacing the current entry point; URL-pattern authorisation rules for `/api/frameworks/\*\*`; static principal configuration (documented cut-line: no persistence of users); Swagger bearer scheme; updated `ApplicationSmokeTest`; a new security test class covering the role matrix, invalid/expired/tampered tokens, and 401-vs-403 semantics; migration of `CompetencyFrameworkApiTests` from `user(...)` to `jwt(...)` post-processors; ADR-010 confirmed (or addended with the platform-web placement ruling); an updated security view diagram; README/token-usage documentation.



\### 9.5 Risks and dependencies

Key generation and format handling in tests (mitigate: Nimbus/`KeyPairGenerator` fixture, fixed test keys); expiry testing flakiness (mitigate: injected `Clock` in the issuer — the Step 2 `TimeConfiguration` was placed with exactly this in mind); correct 401/403 semantics across both authentication failure modes; springdoc security-scheme configuration; scope creep toward user management (hard cut-line: configured principals only, recorded). Dependency: none external — Step 2 baseline suffices.



\### 9.6 Measurable acceptance criteria

1\. All 50 existing tests remain green, with API tests authenticating via real bearer tokens or `jwt()` post-processors.

2\. Unauthenticated request → \*\*401\*\* with `WWW-Authenticate: Bearer`; authenticated-but-unauthorised write → \*\*403\*\*.

3\. LEARNER can `GET`, cannot `POST` (403); EDUCATOR and ADMIN can `POST` (201).

4\. Token endpoint issues an RS256 JWT whose signature, expiry, and roles claim are verified by automated tests; expired and tampered tokens → 401.

5\. Swagger "Authorize → Try it out" completes a full register-and-fetch cycle manually.

6\. Modulith `verify()` and all nine ArchUnit rules pass \*\*unchanged\*\*; `git diff` for the step shows no modifications under `competency/` beyond the API-test authentication mechanics.

7\. ADR-010 status and any addendum recorded; step report delivered; approval gate observed.



\---



\*End of Step 2 completion review. Prepared as a project record for the EGAS dissertation; suitable for inclusion (whole or excerpted) in the design and evaluation chapters. Recommended repository location: `docs/reviews/step2-completion-review.md`.\*

