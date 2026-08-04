# EGAS — Final Project Summary

**Educational Gap Analysis System**
**Student:** Shubham Digamber Biradar (25322206)
**Supervisor:** Dr. Salim Saay
**Programme:** M.Sc. Software Engineering, University of Limerick
**Repository state at summary:** commit `1fe679d`, 33 commits, working tree clean
**Build evidence:** `mvn clean verify` → BUILD SUCCESS · 293 tests · 0 failures · 0 errors · 0 skipped

---

## Statement of authorship and contribution

**The engineering work of this dissertation — the problem definition, the architecture, every
architectural decision and its recorded rationale, the project plan and its phase gates, the
interpretation of requirements into implementable specifications, the review and acceptance of every
change entering the repository, the integration, testing, debugging and validation of the system,
and the dissertation documentation — is the student's own.**

Specifically, the student:

- **defined the problem, objectives and research questions.** The Educational Gap Analysis System,
  its scope and its staged delivery plan (Steps 1–5) originate with the student.
- **planned the project and controlled its execution.** Each step was decomposed into phases with
  explicit entry and exit criteria: inspect before designing, design and obtain approval before
  implementing, review before committing. The student set every scope boundary and refused work that
  fell outside it.
- **interpreted requirements into implementable specifications.** Research goals were translated into
  concrete engineering constraints — framework independence into a runtime-interpreted metamodel,
  boundary discipline into executable fitness functions, explainability into a stored finding that
  carries its own target and provenance.
- **designed the architecture and evaluated the alternatives.** Every structural choice — modular
  monolith over microservices, models-at-runtime over generated code, projection for one context and
  synchronous query for another, relational storage for aggregates with JSONB confined to dynamic
  model artefacts — was selected by the student from stated alternatives, with trade-offs recorded.
- **authored the architectural decisions.** Twenty-two numbered ADRs with eight amendments and
  corrections constitute the decision record. Where implementation revealed a decision to be wrong,
  the student ruled on the correction and required it to be recorded as an amendment rather than
  silently applied — ADR-019 Amendment 1 (derived competency identity) and ADR-017 Amendment 1
  (published identity resolution) are both of this kind.
- **integrated the bounded contexts.** Cross-context contracts, event delivery, the projection
  boundary and the ownership model were specified, sequenced and integrated by the student, who
  decided in each case what a module was permitted to publish and what it was not.
- **reviewed every change and decided what was accepted.** No change entered the repository without
  the student's review and explicit approval, and proposals were frequently modified or rejected on
  engineering grounds (see §10).
- **executed testing, debugging and quality validation.** The student set the standard that
  architectural claims must be mechanically checkable, required verification that tests fail against
  deliberately broken code, directed the diagnosis and correction of defects found during
  development, and gated every phase on a green `mvn clean verify` against real PostgreSQL.
- **owns the Git history.** Commit sequencing, phase boundaries and commit content were the
  student's; nothing was committed without explicit authorisation.
- **produced and approved the evidence artefacts and dissertation documentation.** Completion
  reviews, decision records, diagrams, evidence packs and the documentation-integrity checker exist
  because the student required generated evidence over asserted claims, and each was approved by the
  student before it entered the repository.

**The student takes final responsibility for every committed change in this repository.**

An AI coding assistant was used as an engineering tool to accelerate drafting, implementation and
documentation within this process. It is disclosed in full in **§10**, which should be read as part
of this statement rather than as a footnote to it.

---

## 1. Project overview

EGAS is a model-driven system that compares what a learner is held to have attained against what a
competency framework describes, and produces **explainable** skill gaps — findings that carry not
just a number but the target they were measured against and the evidence that supports them.

The system is built as a **modular monolith** of seven Spring Modulith modules, with a competency
metamodel expressed in EMF/Ecore and interpreted at runtime rather than code-generated. It was
delivered in five steps: metamodel and competency modelling (Steps 1–2), authentication and
authorisation (Step 3), learner profiling (Step 4), and gap analysis (Step 5).

At the point of this summary the system exposes working REST APIs for framework registration,
learner provisioning and evidence recording, and gap analysis; persists to PostgreSQL under Flyway
migrations with one schema per module; and enforces its own architecture through nine automated
checks that run as part of every build.

**Scale.** 8,166 lines of production Java, 6,648 lines of test Java, 6 Flyway migrations, 22
architectural decision records, 293 automated tests.

---

## 2. Research motivation

Competency-based education produces two artefacts that are rarely brought together rigorously: a
**competency framework** describing what capability means and at what levels, and a **learner
record** of evidence about what a person can do. Systems that connect them tend to do so in ways
that are either rigid — the framework hard-coded into the schema — or opaque, producing a
recommendation with no defensible account of why.

Three problems motivated this work.

**Framework rigidity.** A system whose competency model is fixed in its database schema cannot adopt
SFIA, ESCO and a bespoke curriculum framework without redevelopment. EGAS treats the competency
metamodel as a first-class artefact and interprets models at runtime, so a new framework is data
rather than a release.

**Architectural erosion.** Modular monoliths are widely recommended and routinely eroded: a
cross-module join or a convenient import turns a claimed boundary into a fiction, and the erosion is
invisible until extraction is attempted. EGAS treats its boundaries as *testable properties* rather
than conventions.

**Unexplainable analysis.** A gap reported as "competency X: short by 2" is sufficient to sort a
recommendation and insufficient to justify one. A learner cannot act on it and an educator cannot
defend it. EGAS stores what a gap was measured against and the evidence behind the attainment, so a
finding computed in March remains defensible in June after the framework has been revised.

---

## 3. Architecture

**Style.** Modular monolith on Spring Modulith — one deployable, seven modules with explicitly
declared dependencies, verified on every build. The style was selected over microservices because
the research questions concern *boundary discipline*, which a monolith can violate and must
therefore prove it does not; a distributed system would make the property trivially true and the
evidence uninteresting.

**Internal structure.** Every business module follows ports and adapters with four rings — `api`
(the published contract), `domain`, `application`, `infrastructure` — and dependencies pointing
inwards only. The domain ring of every module is framework-free: no Spring, JPA, Hibernate, Servlet
or Jackson types.

**Module dependency graph** (`docs/diagrams/module-dependency.puml`): `learner`, `catalogue` and
`gapanalysis` depend on `competency :: api`; `gapanalysis` additionally on `learner :: api`;
`recommendation` on three published contracts. `platform` — which holds all security machinery —
declares no dependencies and **nothing depends on it**, so security is applied to modules from
outside rather than reached for from inside.

**Persistence isolation.** One PostgreSQL schema per module, cross-schema foreign keys prohibited,
Flyway migrations namespaced by version range. Cross-context references are identifier values,
unvalidated by design and recorded as such.

**Cross-context reads are deliberately asymmetric.** Competency models are **projected
asynchronously** into Gap Analysis by a durable integration event; learner attainment is **read
synchronously** through a published query port. The asymmetry is the decision, not an inconsistency
in it: a competency model is a large, rarely-changing EMF graph that may not cross a module boundary
at all, while attainment is small, per-learner and wanted fresh.

---

## 4. Modules implemented

| Module | Role | Status | Files / lines |
|--------|------|--------|---------------|
| `competency` | Competency Modelling — EMF metamodel (M2), runtime-interpreted models (M1), conformance validation, framework registry | Complete | 38 / 2,110 |
| `learner` | Learner Profiling — profiles, evidence-backed proficiency assertions, substitutable level resolution, ownership | Complete | 41 / 2,247 |
| `gapanalysis` | Gap Analysis — compiled read projection, gap domain, stored explainable reports, REST adapter | Complete | 43 / 2,748 |
| `platform` | Technical module — RSA JWT issuance, resource-server configuration, centralised authorisation | Complete | 16 / 939 |
| `shared` | Shared kernel — one `Identifier` abstraction, no behaviour | Complete | 2 / 28 |
| `catalogue` | Learning Catalogue | Declared stub | 3 / 33 |
| `recommendation` | Recommendation | Declared stub | 3 / 35 |

**Competency Modelling** defines the metamodel programmatically as dynamic Ecore, validates models
against it through EMF's `Diagnostician` plus bespoke invariants standing in for OCL, and persists
each M1 graph as an emfjson document. EMF is confined to this module: no `EObject` crosses its
boundary.

**Learner Profiling** holds an aggregate whose central invariant — at most one assertion per
competency — is enforced both in code and by a database constraint. Evidence is append-only and
collapses into a resolved proficiency level through a substitutable policy port.

**Gap Analysis** is the core domain. Its domain ring is entirely framework-free. A gap report is a
stored, self-contained record: each finding carries the analysis target, the attainment snapshot and
the evidence provenance, so a report remains explicable after its sources have changed.

---

## 5. Technologies used

| Concern | Technology | Selection rationale |
|---------|-----------|---------------------|
| Language / platform | Java 21, Spring Boot 3.4 | Records, sealed types and virtual threads; virtual threads chosen over reactive style to keep blocking code readable |
| Modularity | Spring Modulith 1.3 | Declared module dependencies verified as a build step, not by convention |
| Model-driven engineering | EMF / Ecore (dynamic), emfjson-jackson | Metamodel as a first-class runtime artefact; models are data, not generated classes |
| Persistence | PostgreSQL 16, Flyway, JPA/Hibernate | Schema per module; JSONB confined to dynamic model artefacts |
| Eventing | Spring Modulith durable event publication | Redelivery of an incomplete projection rather than silent loss |
| Security | Spring Security OAuth2 resource server, RS256 JWT, BCrypt | Self-issued tokens; stateless; one centralised filter chain |
| API | REST + OpenAPI (springdoc), RFC 9457 problem details | Structured, machine-readable errors |
| Architecture testing | ArchUnit, Spring Modulith verification | Boundaries as executable checks |
| Testing | JUnit 5, AssertJ, Testcontainers, MockMvc, Awaitility | Real PostgreSQL 16 rather than an in-memory substitute |

---

## 6. Engineering decisions

Twenty-two numbered decisions, thirteen with full text and eight carrying amendments or corrections.
The decisions below are those that most shaped the system.

| ADR | Decision | Why it mattered |
|-----|----------|-----------------|
| 003 | Frozen M2 metamodel, runtime-interpreted M1 | Makes framework independence structural rather than aspirational |
| 007 | CQRS confined to one compiled read projection | Prevents CQRS becoming a system-wide idiom for a single need |
| 011 | One schema per module; no cross-schema foreign keys | Puts the modularity claim at the persistence tier, where it is usually first violated |
| 012 | EMF confined to Competency Modelling | Forces model compilation to happen inside the owning module |
| 015 | Centralised URL-pattern authorisation in one filter chain | The whole access policy readable as one ordered list |
| 016 | Caller identity as command data, not ambient state | Makes ownership testable with no security infrastructure at all |
| 018 | Substitutable level-resolution policy | Isolates the one genuine judgement in evidence handling |
| 019 | Cross-context references unvalidated — recorded as a genuine weakening | Honesty about a limitation, rather than a claim the system cannot support |
| 020 | Relational storage for aggregates; JSONB only for dynamic models | Two invariants become database constraints rather than documentation |
| 021 | Gap as a stored, self-contained finding with substitutable severity | The decision RQ3 rests on |
| 022 | Project competency, query learner | Asymmetry justified by data shape, not symmetry |

**Three decisions were revised under evidence, and the revisions were recorded rather than hidden.**

- **ADR-019 Amendment 1.** Implementation planning found that nothing in the system had ever minted a
  `CompetencyId`: the metamodel identifies competencies by code, so learner references were not
  merely unvalidated but *unmatchable*, and gap analysis could not have joined anything. Identity was
  made deterministically derivable from framework and code.
- **ADR-017 Amendment 1.** Ownership for gap reports proved inexpressible, because no published
  contract resolved an authenticated principal to a learner. A minimal read contract was published;
  the mapping itself did not move.
- **ADR-021.** The planned gap model compared attainment against a "required level" that the
  metamodel does not define. The concept was replaced with an *analysis target* supplied per request,
  and the correction was recorded as a correction.

---

## 7. Testing and validation results

**293 tests, 0 failures**, executed against real PostgreSQL 16 via Testcontainers with Flyway
migrations applied.

| Area | Tests |
|------|-------|
| Gap Analysis | 120 |
| Learner Profiling | 68 |
| Competency Modelling | 48 |
| Platform / security | 42 |
| System and architecture | 15 |

**Nine architecture fitness functions** run on every build — seven ArchUnit rules and two Spring
Modulith verifications. The count has been nine since Step 3; two rules were *strengthened* in place
rather than added to, so the enforcement surface grew without the number changing. Full analysis,
including what these checks deliberately do **not** enforce, is in
`docs/reviews/fitness-function-report.md`.

**Validation approach.** The standard applied throughout was that a passing test proves little on
its own. Key properties were therefore verified by deliberately breaking the implementation,
confirming the intended test failed, and restoring it — seven such verifications are recorded across
Step 5 alone, covering absence handling, severity restoration, the domain-purity rule, ownership
enforcement and non-disclosure. Ownership was proven twice over: once with no security
infrastructure at all, and again over HTTP with real minted tokens rather than test post-processors,
so the token converter and role resolution were genuinely on the path.

---

## 8. Evidence produced

| Artefact | Location | Nature |
|----------|----------|--------|
| Decision log | `docs/adr/` | 22 records with alternatives, trade-offs and amendments |
| Step completion reviews | `docs/reviews/step2–step5-*.md` | Per-step architecture review against ADRs |
| Fitness-function report | `docs/reviews/fitness-function-report.md` | What each check enforces, and what it does not |
| Module dependency diagram | `docs/diagrams/module-dependency.puml` | Enforced DAG with schema ownership |
| Internal module diagrams | `docs/diagrams/*-module-internal.puml` | Ports and adapters per module |
| Metamodel diagram | `docs/diagrams/competency-metamodel.puml` | M2 structure |
| Security evidence pack | `docs/evidence/security/` | Generated transcripts: token issuance, 401, 403 |
| Learner evidence pack | `docs/evidence/learner/` | Generated: ownership matrix, anti-enumeration, resolution cycle, zero-touch |
| Gap analysis evidence pack | `docs/evidence/gapanalysis/` | Generated: explainability chain, absence handling, ownership matrix, non-disclosure, integration cost |
| Documentation integrity checker | `docs/check-doc-references.sh` | Fails on a documentation reference that does not resolve |

**Evidence artefacts are generated, not written.** Each pack is produced by executing the system or
interrogating the repository, so it cannot drift away from the behaviour it documents. This
discipline followed a defect discovered during Step 3: three accepted decision records had cited
artefacts that did not exist, caught by human review rather than by any mechanism. The
documentation-integrity checker closes the cheaply-closable part of that gap and is run as part of
every step's completion.

---

## 9. Research question mapping

The verbatim research questions belong to the dissertation proposal; the mapping below identifies
which artefacts constitute evidence for each theme.

| Theme | Evidence produced |
|-------|-------------------|
| **RQ1 — model-driven framework independence.** Can a competency framework be treated as runtime data rather than as schema? | Dynamic Ecore metamodel with a typed façade; runtime-interpreted M1 models persisted as emfjson; conformance validation driven mechanically from M2 with a 14-code violation catalogue; frameworks with and without proficiency scales both representable. Generated artefact: `competency-metamodel-v1.ecore` |
| **RQ2 — modular boundary discipline and coupling.** Do declared boundaries hold under change, and what does integration actually cost? | Nine fitness functions passing on every build; schema-per-module with no cross-schema keys; and the measured integration-cost sequence — Step 3 and Step 4 each added a complete capability with an **empty diff** under `competency/src/main`, while Step 5 cost that module **+62/−6 across four files**, zero deletions, zero renames, and no published contract signature changed |
| **RQ3 — explainability of computed gaps.** Can a gap be defended rather than merely displayed? | Every stored finding carries its analysis target, the attainment snapshot and the evidence provenance behind it, preserved independently by the domain model, the database schema and the wire format; severity is a substitutable policy rather than arithmetic; absence of measurement is a first-class outcome distinguishable from low attainment |

**On RQ2 specifically.** Steps 3 and 4 demonstrate *consumer isolation*: adding a context that
references another perturbs it not at all. Step 5 demonstrates the harder and more informative
property — *additive integration cost*: what it takes for an existing producer to serve a genuinely
new consumer. The result is one event publication, one derived-identity factory, one exposed
response field and one projection query. The transition from the first property to the second was
anticipated in ADR-022 and measured rather than asserted.

---

## 10. AI and tool assistance disclosure

This section is disclosed in full and in good faith. It should be read alongside the statement of
authorship at the head of this document, not in place of it.

### Tools used

An AI coding assistant (Anthropic Claude, used through an agentic development tool) was used as an
**engineering assistant** across the development of Steps 3, 4 and 5, to accelerate drafting,
implementation and documentation. Standard non-AI tooling was also used: Maven, Git, Docker,
Testcontainers, ArchUnit and Spring Modulith's verification support.

### How it was used

The assistant generated initial implementations of code, tests and documentation from
specifications, architectural decisions and acceptance criteria supplied by the student, and
performed supporting tasks: locating code, summarising existing implementation, presenting design
alternatives for the student to choose between, running builds and tests, and measuring the
repository (for example the integration-cost diffs in §9).

Its role in the workflow was to shorten the distance between a decision the student had made and a
working implementation of it. It did not determine what was built, why, or whether the result was
acceptable. Every output passed through a review gate before entering the repository, and what
survived that gate — rather than what was first proposed — is what constitutes the system described
in this document.

### Engineering review: suggestions modified or rejected

Proposals were frequently altered or refused on engineering grounds. Representative examples, each
traceable in the repository:

- **Rejected: storing the authentication subject on the gap report.** Proposed as the simpler route
  to ownership. The student rejected it because it would copy an identity value into a second
  context's schema and leave an educator unable to act for a learner whose subject they do not hold,
  and directed a published resolution contract instead (ADR-017 Amendment 1).
- **Modified: the scope of the no-breaking-change rule.** The student ruled that the
  zero-breaking-signature criterion applies to published *module* contracts and not to additive REST
  DTO fields, which changed how the integration-cost measurement in §9 was defined and reported.
- **Rejected: framing the end of zero-touch modularity as a regression.** The student directed that
  Steps 3–4 be characterised as evidence of *consumer isolation* and Step 5 as a measurement of
  *additive integration cost* — a reinterpretation that changed the RQ2 argument.
- **Rejected as insufficient evidence:** an integration test that demonstrated a projection was
  populated. The student required proof that delivery actually travelled the durable event registry
  rather than a direct in-process call, and the test was extended accordingly.
- **Rejected: a patch-file delivery method** for a phase, in favour of direct implementation in the
  working tree against a named baseline commit.
- **Modified: a proposed severity port with a single method.** The student accepted a two-method
  design that forces absence of measurement to be answered as a distinct question, and required the
  planning document's acceptance criterion to be amended to match rather than the design to be
  weakened to fit it.

### Evidence of independent judgement

The repository itself carries the record, and it is auditable rather than asserted:

- **ADRs** (`docs/adr/`) state alternatives considered, the choice made, the trade-offs accepted and,
  in eight cases, amendments issued when implementation proved an earlier decision wrong.
- **Completion reviews** (`docs/reviews/`) evaluate each step against its decisions and record
  defects, limitations and open risks rather than only successes.
- **Commit history** shows phase-by-phase delivery with review gates between phases, and includes
  corrective commits where the student directed a defect to be fixed rather than worked around.
- **Evidence packs** (`docs/evidence/`) are generated by executing the system, a discipline the
  student imposed after finding that three accepted decision records had cited artefacts that did not
  exist.

### Limitations and errors

Defects were introduced during development and caught by the student's review process or by the test
suite, including a persistence adapter that produced identifier collisions on re-save, an ordering
contract violation in a value object, an over-broad exception translation, and source files placed
outside the Maven module so that their tests never ran. These are recorded in the step completion
reviews. Their existence is part of why the review gates described above were imposed, and is itself
evidence about the appropriate role of such tools in engineering work: acceleration is useful, and
unreviewed acceleration is not.

### Statement

The student takes responsibility for all content of this repository and dissertation, including code
the assistant helped produce, and is able to explain and defend the design, the decisions and the
implementation. The architecture, the decisions and their rationale, the integration, the testing
and validation strategy, and the critical evaluation recorded throughout are the student's own
contribution.

*The student should verify this section against the University of Limerick's current policy on the
use of generative AI in assessed work and adjust the wording to whatever declaration format that
policy requires.*
