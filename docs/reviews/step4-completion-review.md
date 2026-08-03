# EGAS Architecture Review — Step 4 Completion

**Project:** Educational Gap Analysis System (EGAS) — Architectural Design of a Model-Driven Educational Gap Analysis System with AI-Based Recommendation Support

**Student:** Shubham Digamber Biradar (25322206) · **Supervisor:** Dr. Salim Saay · University of Limerick, M.Sc. Software Engineering

**Review date:** 3 August 2026

**Baseline under review:** Step 4 — Learner Profiling, commits `9e27a47` … Phase 5, on `main`

**Verified build evidence:** `mvn clean verify` → BUILD SUCCESS · Tests run: **152** · Failures: 0 · Errors: 0 · Skipped: 0 · executed against PostgreSQL 16 via Docker/Testcontainers with Flyway applied

---

## 1. Executive summary

Step 4 delivered the Learner Profiling bounded context as a complete vertical slice: an
evidence-backed aggregate with a substitutable resolution policy, a relational persistence adapter,
transactional use cases, and a web adapter enforcing resource ownership. The suite grew from 92 to
152 tests.

Two results matter beyond the feature itself.

**Zero-touch modularity is now a trend rather than an anecdote.** An entire bounded context —
domain, persistence, application and web — was added across Steps 3 and 4 with an empty
`git diff` under `competency/src/main` in both. The `learner` module's `allowedDependencies`
remains `{"competency :: api"}`, and no runtime call passes between the two contexts. This is the
RQ2 coupling claim in checkable form.

**The ADR-016 identity decision paid off exactly as argued.** Because the caller's subject travels
as command data rather than ambient state, the complete ownership matrix is exercised in
`LearnerProfileServiceTests` with no security context, no token, no filter chain and no Spring
container. The claim was made in Step 3 Phase 0 before any code depended on it; Phase 3 is where it
became demonstrable, and the strengthened `applicationStaysOutOfAdapters` rule now enforces it
rather than trusting it.

**No architecture rule was edited.** The seven ArchUnit fitness functions and two Spring Modulith
verifications passed unmodified throughout, and began covering each new ring automatically as it
appeared.

**Readiness:** **GREEN.** All ten Definition-of-Done criteria are met (§6).

---

## 2. Objectives achieved

| Objective | Outcome |
|---|---|
| Learner profile aggregate with evidence-backed proficiency | `LearnerProfile` + `ProficiencyAssertion` + `EvidenceRecord`, append-only |
| Substitutable level-resolution policy | `LevelResolutionPolicy` port, `HighestConfidenceResolutionPolicy` default, lambda-substitutable |
| Relational persistence in an isolated schema | `V200__`, three tables, intra-schema FKs only |
| Explicit provisioning and subject→profile mapping | `POST /api/learners/me`, `findByAuthSubject`, unique constraint |
| Ownership authorisation without security leaking inward | Aggregate predicate + application enforcement + adapter role resolution |
| Anti-enumeration on individual resources | 404 for denial and absence alike, asserted at unit and HTTP level |
| Zero-touch on the referenced module | Empty diff under `competency/src/main` |

---

## 3. ADR realisation

| ADR | Decision | Realisation |
|---|---|---|
| **015 A1** | Ownership at the application layer; coarse rules in one chain | `SecurityConfig` two rules; `LearnerProfileService.getProfileForReader`; 404 non-disclosure |
| **016** | Identity as command data, never ambient | `AuthSubject` on commands; controller extracts from JWT; zero security imports below the adapter |
| **017** | Subject as aggregate attribute; explicit provisioning | `uq_learner_auth_subject`; `POST /me`; check-then-act closed by constraint |
| **018** | Immutable evidence; resolution as a port | `EvidenceRecord`; policy bean in `LearnerModuleConfiguration` |
| **018 A1** | `recordedAt` system-assigned | Service stamps from injected `Clock`; command carries no timestamp |
| **019** | Cross-context references unvalidated | `competency_id`/`framework_id` bare UUIDs, no FK, no query port |
| **020** | Learner state relational; jsonb confined to model artefacts | Three tables; constraint-backed invariants |
| **011** | Schema per module, V200–V299, no cross-schema FK | `V200__create_learner_profile_tables.sql` |
| **012** | EMF confined to Competency Modelling | Zero EMF imports in `learner` |
| **001/008** | Modular monolith, boundaries enforced | DAG unchanged; nine architecture tests unmodified |

Four ADRs were authored in this step (017, 018, 019, 020) and one amended twice (015 A1; 018 A1).

---

## 4. Test evidence

**152 tests, 0 failures**, on real PostgreSQL. Learner contributes 50:

| Suite | Tests | Establishes |
|---|---|---|
| `LearnerProfileAggregateTests` | 10 | Invariants; ownership predicate; unmodifiable views; reconstitution fidelity; `reconstitute` trust boundary |
| `LearnerValueObjectTests` | 6 | Validation, normalisation, `Comparable`/`equals` consistency |
| `LevelResolutionPolicyTests` | 8 | Confidence dominance, all three tie-breaks, order-independence, substitutability |
| `LearnerProfileServiceTests` | 11 | **Ownership matrix with no security infrastructure**; provisioning; system-assigned timestamp |
| `JpaLearnerProfileRepositoryTests` | 11 | Field-by-field round trip; update accumulation; constraint translation; projection loading zero entities |
| `LearnerProfileApiTests` | 14 | HTTP contract and the full role/ownership matrix with real minted tokens |

Three testing decisions are worth recording. Assertions compare **field-by-field** where entity
equality is by identifier, because `isEqualTo` on such types passes vacuously. Integration suites
use **real minted tokens** rather than `jwt()` post-processors, since the claim-to-authority
converter and the `callerMayReadAny` resolution are precisely what a post-processor bypasses. And
performance claims are **measured** — Hibernate statistics confirm `findById` issues one query and
`findAllSummaries` materialises zero entities.

---

## 5. Architecture checks

- Nine architecture tests pass **unmodified**: `HexagonalArchitectureTests` (7), `ModularityTests` (2).
- `learner.domain` and `learner.application` contain **zero** Spring Security imports and no `ROLE_` literals.
- `ROLE_EDUCATOR`/`ROLE_ADMIN` appear in exactly two files: the learner controller and `SecurityConfig`.
- No `ie.ul.egas.platform` import exists anywhere in `learner`.
- `git diff` under `competency/src/main` empty across the whole step.
- `ddl-auto: validate` passes, so schema and mappings cannot silently drift.

---

## 6. Definition of Done

| # | Criterion | Status |
|---|---|---|
| 1 | `mvn verify` BUILD SUCCESS, ≈137+ tests, zero failures on real PostgreSQL | **Met** — 152 |
| 2 | Ownership matrix proven cell-by-cell with real tokens | **Met** |
| 3 | `404` non-disclosure verified | **Met** |
| 4 | Nine architecture tests pass unmodified | **Met** |
| 5 | Empty `git diff` under `competency/src/main` | **Met** |
| 6 | `learner` `allowedDependencies` unchanged | **Met** |
| 7 | ADR-017/018/019 Accepted and ADR-015 amended | **Met** (+ ADR-020) |
| 8 | Module diagram committed | **Met** — rewritten in Phase 5 for all four rings |
| 9 | Step report delivered | **Met** — this document |
| 10 | Stop-and-wait observed | **Met** |

---

## 7. Defects found and fixed during the step

Four defects were found by review or measurement rather than by a failing test, and each is
recorded because the *manner of discovery* is itself a finding.

**`AttainedLevel` violated the `Comparable`/`equals` contract.** Ordinal-only ordering made two
distinct levels compare equal, and because the resolution policy chains that ordering as its final
tie-break, evidence differing only in level code resolved according to storage order — contradicting
ADR-018's promise of determinism. Fixed by a code tie-break; the regression tests were verified to
fail against the old implementation before the fix was restored.

**The persistence adapter could not save an existing profile.** Evidence row ids were generated
randomly, so a re-save inserted a replacement set colliding with its own orphans on
`uq_evidence_assertion_seq`. Recording evidence against an existing profile is the module's primary
use case; the suite missed it because every test saved once. Fixed by deriving row ids from
`(assertionId, seq)`.

**Exception translation was indiscriminate**, reporting every integrity violation as a duplicate
profile — the probe that found the previous defect was shown it mislabelled. Narrowed to the
auth-subject constraint.

**Three Accepted ADRs cited artefacts that did not exist**: a module diagram, a database table, and
a test class. All three were corrected. Because the pattern recurred, Phase 5 added
`docs/check-doc-references.sh`, which resolves backticked repository paths and test-class names in
`docs/*.md` and fails on a dangling reference. It found a fourth instance immediately: the Step 2
completion review cites `src/test/resources/archunit.properties`, which does not exist. Per the
governance practice that committed historical reviews are not modified, that inaccuracy is recorded
here and allowlisted rather than edited away.

---

## 8. Limitations and accepted trade-offs

**Accepted, recorded in ADRs.** Cross-context references are unvalidated (ADR-019) — a profile may
hold assertions against competency identifiers that never existed, surfacing later as absence from
analysis. Evidence accumulates without pruning or supersession, and resolution runs on write, so a
policy change does not retroactively re-resolve stored assertions (ADR-018). Confidence is
caller-supplied and unverified. Educator-recorded observations are deliberately deferred: the write
path supports self-recording only, though `EvidenceType.OBSERVATION` anticipates the capability.

**Accepted, structural.** The role vocabulary exists in two places — `SecurityConfig` derives it
from the `Role` enum, the learner controller compares literal authority strings — because the
module DAG forbids `learner` from referencing `platform`. This is the same class of trade-off
ADR-015 already records for URL paths named in both the chain and the handler, and the role matrix
is its guard.

**Open, low severity.** Derived evidence row ids depend on ADR-018's append-only guarantee and must
be revisited if supersession lands. Aggregate load cost as evidence accumulates is documented as
bounded but has not been measured (scheduled W11). `recordEvidence` stamps nanosecond precision
into a microsecond column; harmless for ordering, unasserted by any test. ADRs 001–010 still have
no full text, now cited from six places including ADR-020's Problem section.

---

## 9. Next-step recommendations

**Step 5 should be Gap Analysis.** Its `allowedDependencies` are `{"competency :: api",
"learner :: api"}` and both are now delivered, so it is unblocked; `gap_analysis` holds the
V400–V499 range, and ADR-007 already fixes its CQRS scope to a compiled read projection rebuilt
from integration events.

Two items should be scheduled independently of it. Transcribing ADRs 001–010 from the design log is
now overdue and is cited from an increasing number of records. And the W11 aggregate-load
measurement should happen before evidence volumes make it awkward to characterise.

Gap Analysis will also retire ADR-019's accepted weakness: once the projection exists, an assertion
naming an unknown competency becomes detectable where the data already is, without a synchronous
call and without either context depending on the other at runtime.
