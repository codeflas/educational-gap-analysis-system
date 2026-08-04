# EGAS Architecture Review — Step 5 Completion

**Step:** 5 — Gap Analysis (the dissertation's core domain)
**Reviewed at:** commit `1fe679d`, working tree clean
**Baseline:** Step 4 complete at `905b3b7`, 152 tests green
**Verified build evidence:** `mvn clean verify` → BUILD SUCCESS · Tests run: **293** · Failures: 0 ·
Errors: 0 · Skipped: 0 · executed against PostgreSQL 16 via Docker/Testcontainers with Flyway applied
**Documentation reference check:** clean
**Governing decisions:** ADR-007, ADR-011 A1, ADR-015 A2, ADR-016, ADR-017 A1, ADR-019 A1/A2,
ADR-020, ADR-021, ADR-022 A1

---

## 1. Executive summary

Step 5 delivers Gap Analysis end to end: a compiled read projection fed by a durable integration
event, a framework-free gap domain, relational storage for authored reports, an application service
carrying the ownership rules, and a REST adapter. Gap computation now produces findings that carry
what they were measured against, what the learner was held to have attained, and the observations
behind that — which is the form RQ3's explainability claim has to take to be checkable at all.

Three results are worth separating from the delivery.

**The zero-touch property ended, as ADR-022 said it would, and the cost was measured rather than
claimed.** Steps 3 and 4 each added a complete bounded context with an empty diff under
`competency/src/main`. Step 5 is the first step to change that module, and §7 records the change line
by line: **+62/−6 across four existing files**, zero deletions, zero renames, and no published module
API signature altered. Steps 3–4 demonstrated *consumer isolation*; Step 5 demonstrates *additive
integration cost*, which is the harder and more informative result.

**Two planning assumptions were found to be wrong, and both were recorded rather than quietly
corrected.** Planning found that nothing in the system had ever minted a `CompetencyId` — learner
references were not merely unvalidated but *unmatchable* (ADR-019 A1). Phase 4a then found that
ownership for gap reports was inexpressible, because `learner.api` published no way to resolve a
principal to a learner (ADR-017 A1). Each was surfaced as a finding, ruled on, and implemented as an
amendment; neither was worked around.

**The nine architecture checks passed unmodified in count throughout**, with two strengthened in
place. `domainIsFrameworkFree` gained `org.hibernate..` after Phase 4a; the hole it closed was real
and is documented in the fitness-function report.

The step is complete against its Definition of Done (§6). Six risks remain open at submission (§10),
of which one — nine ADRs existing only as index rows — is a documentation risk this step cannot fix
without inventing decisions.

---

## 2. Objectives achieved

| # | Objective | Outcome |
|---|-----------|---------|
| 1 | Published read contracts for both upstream contexts | `competency.api` gained `CompetencyModelSnapshot` and `CompetencyModelRegistered`; `learner.api` gained `LearnerAttainmentQuery`, `AttainedCompetency` and `LearnerIdentityQuery` |
| 2 | Durable event delivery | Spring Modulith publication registry in `common` (ADR-011 A1); delivery proven to travel the registry, not a direct call |
| 3 | Compiled competency-model projection | `V400`, four tables, replace-on-write, idempotent under redelivery |
| 4 | Framework-free gap domain | `GapReport`, `SkillGap`, three snapshots, `GapSeverityPolicy` + default; no Spring, JPA, Hibernate, Modulith, EMF or web types |
| 5 | Stored, explainable reports | `V401`, three tables; target, attainment and provenance retained per finding |
| 6 | Ownership enforcement | `GapAnalysisService`, two denial shapes, matrix proven with no security infrastructure |
| 7 | REST adapter | `/api/gap-reports`, three endpoints, RFC 9457 errors, one filter-chain rule |
| 8 | Integration cost measured and reported | §7 |

---

## 3. ADR realisation

| ADR | Realised as | Notes |
|-----|-------------|-------|
| 007 (CQRS scope) | `V400` projection, rebuilt from `CompetencyModelRegistered` | Text was reconstructed from repository evidence in Step 5 planning; provenance table retained in the ADR |
| 011 A1 (registry home) | `common.event_publication` via `V2` | Framework-managed metadata no module could own |
| 015 A2 (gap ownership) | One chain rule + predicate in `GapAnalysisService` | Two denial shapes; see §4 |
| 016 (identity propagation) | `authSubject` as a command field on every ownership-sensitive method | No `SecurityContextHolder` anywhere in application or domain |
| 017 A1 (identity resolution published) | `LearnerIdentityQuery` + adapter | The mapping does not move; only the resolution is published |
| 019 A1 (derived identity) | `CompetencyId.forCompetency(frameworkId, code)` | The join key that made gap computation possible at all |
| 019 A2 (closure) | Precondition met, validation outstanding | §9 and the ADR itself |
| 020 (relational persistence) | `V401` applies the same shape test to gap reports | Two invariants expressible only as constraints |
| 021 (gap model) | Domain, storage and wire format all preserve the explainability chain and absence | §4 |
| 022 A1 (read strategy) | Competency projected, learner queried — twice | Producer diff measured in §7 |

**No ADR was contradicted, and no ADR decision was invented.** ADRs 001–006 and 008–010 remain index
rows without full text (§10, R3); this review cites them only where the index entry alone is
sufficient.

---

## 4. Test evidence

**293 tests, 0 failures**, on real PostgreSQL. Step 5 added **141** (152 → 293).

| Area | Tests | What it covers |
|------|-------|----------------|
| Gap Analysis | **120** | Domain, policy, projection, persistence, application, web |
| Learner Profiling | 68 | Aggregate, resolution policy, persistence, two published contracts, web |
| Competency Modelling | 48 | Metamodel, aggregate, conformance, compiler, persistence, web |
| Platform | 42 | Keys, tokens, principals, authorisation matrix, OpenAPI |
| System / architecture | 15 | Smoke, event registry, 9 fitness functions |

Gap Analysis's 120 break down as: framework-free domain 49 (`GapReportTests` 15,
`GapValueObjectTests` 11, `SkillGapTests` 10, `OrdinalDistanceSeverityPolicyTests` 7,
`ProjectedCompetencyModelTests` 6), application 19 (`GapAnalysisServiceTests`), persistence 19,
web 20 (`GapReportApiTests`), integration 13.

### Properties proven rather than asserted

**Absence survives three layers.** An unassessed finding holds no `AttainmentSnapshot`
(`SkillGapTests`), stores three null columns under `ck_skill_gap_attainment_complete`
(`JpaGapReportRepositoryTests`), and omits the field entirely on the wire (`GapReportApiTests`). The
distinction ADR-021 protects is therefore preserved by the domain, the schema and the serialiser
independently — no single layer is load-bearing for it.

**A stored judgement is restored, not recomputed.** `JpaGapReportRepositoryTests` stores `MET` on a
finding three levels short — a value no policy in the codebase would produce — and asserts it
survives reload.

**Ownership is provable without security infrastructure.** `GapAnalysisServiceTests` (19 tests) runs
the whole matrix with no security context, no token and no Spring container — the ADR-016 payoff in
its checkable form. `GapReportApiTests` then re-proves it over HTTP with **real minted tokens**
rather than `jwt()` post-processors, so the converter, the controller's role collapse and the
identity contract are all genuinely on the path.

**Non-disclosure is asserted as a byte comparison.** A forbidden report and an absent one must
produce identical RFC 9457 bodies apart from `instance`, which echoes the caller's own path.

### Mutation verification

Passing tests prove little on their own, so seven deliberate defects were introduced across the step,
observed to fail exactly the intended tests, then reverted with the suite re-run green.

| Phase | Defect introduced | Tests that caught it |
|-------|-------------------|----------------------|
| 3 | Uniqueness check removed from `GapReport` | 2 |
| 3 | Absent attainment collapsed to ordinal zero | 2 |
| 4a | Absence mapped to a zero snapshot on load | 1 |
| 4a | Severity recomputed on load | 1 |
| 4a | `@Immutable` on a domain record | `domainIsFrameworkFree` — which **passed** under the previous rule wording |
| 4b | Ownership comparison disabled | 6, across unit and integration suites |
| 5 | 404 body echoing `getMessage()` | 1, with the leaked report id visible in the diff |

---

## 5. Architecture checks

Nine checks, all passing, count unchanged since Step 3. Two were strengthened in place during the
step — `applicationStaysOutOfAdapters` by ADR-016 (earlier) and `domainIsFrameworkFree` by Phase 4a.
Full analysis, including what each check does *not* enforce, is in
`docs/reviews/fitness-function-report.md`.

The module DAG gained **no edge** in Step 5: `gapanalysis` already declared
`allowedDependencies = {"competency :: api", "learner :: api"}`, and both were exercised for the
first time rather than newly permitted. Rendered in `docs/diagrams/module-dependency.puml`; the
module's internal structure is in `docs/diagrams/gapanalysis-module-internal.puml`.

---

## 6. Definition of Done

| # | Criterion | Status |
|---|-----------|--------|
| 1 | `mvn verify` BUILD SUCCESS, ≈180+ tests, zero failures on real PostgreSQL | **Met** — 293 |
| 2 | An event published by Competency Modelling populates the Gap Analysis projection | **Met** — `CompetencyModelProjectionIntegrationTests`, including registry-path proof |
| 3 | Gap computation produces explainable findings carrying target, attainment and provenance | **Met** — `GapAnalysisIntegrationTests`, end to end across three contexts |
| 4 | Ownership matrix and 404 non-disclosure proven with real tokens | **Met** — `GapReportApiTests` |
| 5 | Nine architecture tests pass unmodified | **Met** — count unchanged; two strengthened, both recorded |
| 6 | Producer-side integration cost measured and reported | **Met** — §7 |
| 7 | Documentation references check clean | **Met** |
| 8 | ADR-019 closure note recorded | **Met** — as precondition-met, validation-outstanding (§9) |
| 9 | Step report delivered | **Met** — this document |
| 10 | Stop-and-wait observed at every phase gate | **Met** — six phases, each reviewed before commit |

---

## 7. Integration cost

The RQ2 measurement. Scope: `src/main` only, `905b3b7 → 1fe679d`, existing modules only.

### What Step 5 cost the modules it consumes

| Module | Existing files modified | Lines | New files added | Deletions | Renames |
|--------|------------------------|-------|-----------------|-----------|---------|
| Competency Modelling | 4 | **+62 / −6** | 4 (+194) | 0 | 0 |
| Learner Profiling | 1 | **+10 / −0** | 5 (+224) | 0 | 0 |
| Platform | 1 | **+12 / −1** | 0 | 0 | 0 |

Modified files, competency: `CompetencyId` (+25, a static factory), `CompetencyFrameworkService`
(+17/−1, one publisher field and one publish call), `FrameworkWebMapper` (+13/−4),
`FrameworkDetailResponse` (+7/−1). Modified file, learner:
`LearnerProfileSpringDataRepository` (+10/−0, one projection query, of which three lines are code).
Modified file, platform: `SecurityConfig` (+12/−1, one chain rule and comments).

### Were any signatures broken?

All six deleted lines under `competency` were inspected individually. Five are method bodies or
*private* mapper signatures. One is a signature change: `FrameworkDetailResponse.CompetencyResponse`
gained a `competencyId` component. That is a REST DTO, not a published module contract — the
distinction ruled on before Phase 1, and the field exists so clients obtain real identifiers instead
of inventing them (ADR-019 A1). `competency.api` gained **only new types**, and `CompetencyId` gained
a static factory, which is purely additive.

**Zero breaking changes to published module API contracts** therefore holds, and holds provably.

### The trend across three steps

| Step | Capability added | Change to `competency/src/main` |
|------|------------------|--------------------------------|
| 3 | Complete security subsystem | **empty diff** |
| 4 | Complete bounded context (Learner Profiling) | **empty diff** |
| 5 | Complete bounded context (Gap Analysis), consuming both | +62/−6, four files, zero deletions |

Steps 3 and 4 measured **consumer isolation**: adding a context that *references* another perturbs
it not at all. Step 5 measures **additive integration cost**: what it takes for an existing producer
to serve a genuinely new consumer. The second is the harder question, and the answer — one event
publication, one derived-identity factory, one exposed field, and one projection query — is small
enough to state in a sentence.

### Relative module size

| Module | Files | Lines | Inbound published contracts |
|--------|-------|-------|-----------------------------|
| `gapanalysis` | 43 | 2,748 | 2 |
| `learner` | 41 | 2,247 | 1 |
| `competency` | 38 | 2,110 | 0 |
| `platform` | 16 | 939 | 0 (nothing may depend on it) |
| `shared` | 2 | 28 | — (shared module) |

Gap Analysis is the largest module and the only one consuming two contexts, which is what a core
domain that sits downstream of everything should look like.

---

## 8. Scope boundary decisions

Things deliberately **not** built, each with the reason and the trigger that would change it.

| # | Not built | Why | What would change it |
|---|-----------|-----|----------------------|
| 1 | Reference-validation report for orphaned competency ids | The precondition landed in this step; the report needs a contract that does not exist (§9) | A profile health-check requirement |
| 2 | `GET /api/gap-reports/me` | Gap Analysis has no notion of "me" — only Learner Profiling does. Adding it would widen the application layer for ergonomics | A client requirement that the two-call flow is unacceptable |
| 3 | Automatic report invalidation when evidence changes | ADR-021 accepts staleness explicitly; recomputation is an explicit act, as provisioning is under ADR-017 | Learner Profiling emitting an evidence-changed event; the natural shape is a stale-report *report*, not deletion |
| 4 | Model publication lifecycle | `ModelStatus.PUBLISHED` exists but nothing sets it. Introducing a lifecycle is a Competency Modelling concern with its own authorisation questions (ADR-022) | An editorial workflow requirement; the projection would then filter on state without changing shape |
| 5 | `GapReportId` promoted to `gapanalysis.api` | No consumer addresses a whole report today; publishing on speculation widens the contract | Recommendation consuming reports rather than individual gaps — the promotion is additive |
| 6 | Additional severity policies | ADR-021 provides the port; one default is enough to prove substitutability | An institution with a different rule — a new class and a bean change |
| 7 | Recommendation and Catalogue | Out of Step 5 scope; both remain declared stubs with one published identifier each | Step 6 |
| 8 | Educator-recorded evidence | `EvidenceType.OBSERVATION` anticipates it; ADR-018's command is self-only by design | A deliberate widening, needing a target learner and a `callerMayWriteAny` decision |

---

## 9. ADR-019 status at completion

Recorded in full as **ADR-019 Amendment 2**. Summarised here because it is a Definition-of-Done item.

**Precondition: met.** The three things reference validation needed all exist. Identity is derived
from `(frameworkId, code)`, so both sides can compute the same key without reading each other's
tables. The projection holds every competency of every registered framework, keyed by that identity.
And the published attainment contract carries a learner's assertions across the boundary. Before
Step 5 none of this was true, and the check ADR-019 deferred could not have been written at all.

**Validation: outstanding, and not a small remainder.** `GapAnalysisService` iterates the *model's*
competencies and looks up attainment, so an assertion naming a competency absent from the model is
silently ignored — exactly the degradation ADR-019 predicted, and not a report. Producing the
profile health check its Future Evolution anticipates needs orphans across *all* learners, and
`LearnerAttainmentQuery` is per-learner while ADR-011 forbids the cross-schema join that would make a
system-wide sweep cheap. **Closing ADR-019 fully therefore requires a new published contract**, which
is a decision to take rather than a task to schedule.

ADR-019's substantive decision — cross-context references are unvalidated at write time — is
unchanged and remains correct.

---

## 10. Risk register at submission

| # | Risk | Severity | Assessment |
|---|------|----------|------------|
| R1 | Two commits share the subject line "phase 4b" (`7b16057` code, `faedb88` docs) | Low | Content is not duplicated. Commit history is dissertation evidence, so the ambiguity is worth naming; rewriting history this late is the larger risk |
| R2 | ADR-019 closed as precondition-only | **Medium** | Stated plainly in §9 and in the ADR. An examiner reading the Future Evolution section will ask; the answer must not overclaim |
| R3 | ADRs 001–006 and 008–010 exist only as index rows | **High** | The largest documentation risk at submission. ADR-008 in particular carries the enforcement claim this project rests on. Cannot be fixed without inventing decisions — the design log is the only source |
| R4 | Test isolation depends on sequential execution | Low | `LearnerProfileApiTests` and `GapReportApiTests` truncate shared tables in `@BeforeEach` against one database. Safe today; enabling JUnit parallelism would break both |
| R5 | `GapReportApiTests` leaves a framework and projection per test | Low | Twenty accumulate per run. No test asserts a global framework count today — verified — so this is fragility, not a defect |
| R6 | Eventual consistency is user-visible | Low | `POST /api/gap-reports` answers 422 for a framework registered seconds earlier. Correct under ADR-007/022, and stated in the evidence pack so it is not discovered by an examiner instead |
| R7 | No end-to-end performance evidence | Medium | Query counts are bounded and asserted (≤3 projection read, ≤2 report load), but the W11 aggregate-load measurement outstanding since earlier steps still has no timing over a realistic framework |

---

## 11. Next-step recommendations

1. **Transcribe ADRs 001–006 and 008–010 from the design log** before anything else. R3 is the
   highest-value remaining work and nothing in the codebase can substitute for it.
2. **Decide ADR-019's endgame explicitly** — either publish the contract a system-wide orphan report
   needs, or record that per-learner detection is sufficient and close the ADR on that basis. Either
   is defensible; leaving it open is the weakest option.
3. **Take the W11 measurement** against a realistic framework, closing R7 with data rather than
   query-count proxies.
4. **Step 6 (Recommendation)** consumes `gapanalysis.api`, which today publishes one identifier. The
   first decision of that step is whether it consumes gaps or reports — and that decides the
   `GapReportId` promotion left open in §8.
5. **Leave the commit history alone.** R1 is cosmetic and a rebase this close to submission risks
   more than it repairs.
