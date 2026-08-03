# EGAS Step 4 — Learner Profiling: Architectural Inspection and Implementation Plan

**Status:** PROPOSED — inspection for review; no implementation authorised
**Date:** 3 August 2026
**Source of truth:** repository snapshot `egas-head.zip` (183 files). Every statement below is
derived from that tree; nothing is carried over from prior conversation state.
**Baseline verified in-snapshot:** 92 tests (85 `@Test` + 7 `@ArchTest`), ADRs 001–016 all
Accepted, seven ArchUnit fitness functions plus two Spring Modulith verifications.

**Amendment log — A1 (3 Aug 2026, recorded after Phase 2 implementation):** §9 proposed storing
evidence in a `jsonb` column on `proficiency_assertion`. **ADR-020 (Accepted) supersedes that
proposal**: learner state is persisted in relational tables, and `jsonb` remains confined to the
dynamic model artefacts of the Competency Modelling context. The superseded proposal and the
reasoning that displaced it are retained in §9.1 rather than deleted, so the change of direction is
readable rather than silent. §11's persistence row, §12's manifest, §13's risk register and §14's
Phase 2 row are updated to match. No other section is affected, and no earlier decision is altered.

---

## 1. Current Architectural Assessment

### 1.1 Repository shape
The repository root holds `docs/` (27 files) and `egas/` (the Maven project). Documentation
consolidation is complete: ADRs, diagrams, planning, reviews and the Step 3 security evidence
pack all live at root level. **This matters operationally** — Step 3's one process failure was a
commit landing at root `src/` instead of `egas/src/`, where it never compiled. Every path in §12
is therefore given repository-root-relative, with the `egas/` prefix explicit.

### 1.2 Learner module stubs
Three files only:

| File | Content |
|---|---|
| `learner/package-info.java` | `@ApplicationModule(displayName = "Learner Profiling", allowedDependencies = {"competency :: api"})` |
| `learner/api/package-info.java` | `@NamedInterface("api")` |
| `learner/api/LearnerId.java` | UUID record implementing `shared.Identifier`, with `random()` and `of(String)` |

`LearnerId`'s javadoc carries a Step-1 instruction that directly governs this step: *"Distinct
from any authentication principal id on purpose: the security identity (platform concern) and the
domain identity (profiling concern) must be free to evolve independently; the mapping between them
is an application-layer concern."* The module javadoc states the context's purpose as *"evidence
ingestion and resolution of proficiency levels against competencies"* — evidence and resolution
are named, so a profile of bare self-declared levels would under-deliver against the module's own
stated charter.

### 1.3 Modulith boundaries
```
competency      → {}                                          (frozen, self-contained)
learner         → {competency :: api}
catalogue       → {competency :: api}
gapanalysis     → {competency :: api, learner :: api}
recommendation  → {competency :: api, gapanalysis :: api, catalogue :: api}
platform        → {}   (technical module; nothing may depend on it)
shared          → {}
```
Two consequences bind Step 4. First, `learner` **cannot reference any type in `platform`** — which
is precisely why ADR-016 exists, since all security machinery lives there. Second, `gapanalysis`
already declares a dependency on `learner :: api`: whatever Step 4 publishes there becomes a
frozen downstream contract for Step 5/6.

### 1.4 Hexagonal rules (seven ArchUnit + two Modulith = nine architecture tests)
`domainIsFrameworkFree`, `domainDoesNotReachOutwards`, `applicationStaysOutOfAdapters`,
`restControllersOnlyInWebAdapters`, `publishedContractsArePure`, `emfConfinedToCompetencyModule`,
`emfSerializationStaysOutOfDomain`; plus `moduleTopologyIsValid` and
`generateArchitectureDocumentation`.

Three bind this step sharply:

- **`applicationStaysOutOfAdapters` now bars `org.springframework.security..`** (Phase 0
  hardening, per ADR-016). A `SecurityContextHolder` read inside `LearnerProfileService` would
  fail the build. The mechanism ADR-016 mandates is not merely preferred — it is enforced.
- **`emfConfinedToCompetencyModule`** means the learner domain must be entirely EMF-free.
  Competencies are referenced by `CompetencyId` value, never by `EObject`. Learner Profiling is a
  conventional DDD context, not a second MDE context — worth stating in the dissertation, because
  it shows ADR-012's boundary doing real work rather than sitting decorative.
- **`publishedContractsArePure`** restricts `learner.api` to `java..`, `ie.ul.egas.shared..`,
  `..api..` and `org.springframework.modulith..`. No Jackson, no persistence types, no Spring.

### 1.5 ADR-011 schema allocation and Flyway
`V1__create_module_schemas.sql` already created the `learner` schema. `application.yml` already
lists `classpath:db/migration/learner` among Flyway locations, with
`fail-on-missing-locations: false` — so **no configuration change is required**; creating the
directory is sufficient. Range **V200–V299** is reserved and unused. The V1 migration's header
states the binding rule: *no cross-schema foreign keys, ever*; intra-schema foreign keys remain
mandatory.

### 1.6 ADR-016 — identity propagation (decisive)
Accepted 3 Aug 2026. The controller obtains the subject via `@AuthenticationPrincipal Jwt jwt` and
places `jwt.getSubject()` into the command; application services receive it as an ordinary
parameter and evaluate ownership against the loaded resource; domain types receive a plain
identifier. The ADR is explicit that command construction must **never** take the subject from the
request body — the controller is the only legitimate source. It also anticipates this step's
shape: *"Should ownership checks proliferate, a domain-level `Owned` abstraction with a shared
assertion helper is the natural consolidation."*

This resolves the identity question that would otherwise have been Step 4's largest open fork.
What it does *not* resolve is how a subject string maps to a `LearnerId` — that is §2/§13's
candidate ADR-017.

### 1.7 ADR-015 and the ownership gap
ADR-015 confines coarse authorisation to ordered `requestMatchers` in the single filter chain and
records honestly that *"URL patterns are coarse: they cannot express 'this learner may read their
own profile'."* Its Future Evolution section names Step 4 as the designated extension point and
states the amendment *"is to be recorded as an amendment to this ADR when it lands."* **Step 4
therefore owes ADR-015 an amendment, not merely a new ADR.**

Current ordered rules: health/info and API docs permitted; `POST /auth/token` permitted;
`GET /api/frameworks/**` authenticated; remaining verbs on `/api/frameworks/**` restricted to
`EDUCATOR`/`ADMIN` via `Role` enum constants; `anyRequest().authenticated()`. Roles: `EDUCATOR`,
`LEARNER`, `ADMIN`, closed enum, claim name `roles`, prefix `ROLE_` applied at one converter.

### 1.8 competency::api contracts
Exactly two published records: `CompetencyId` and `CompetencyFrameworkId`, both UUID-backed
`Identifier` implementations. **There is no published query port** — no contract answering "does
this competency exist?" or "what levels does this framework define?" The competency metamodel
holds `ProficiencyLevel(code[1], name[0..1], ordinal[1])` as an M1 element *inside* the model, and
no level identifier is published. This is a genuine design fork for Step 4 (§13, candidate
ADR-019).

### 1.9 Testing conventions
Classes named `*Tests` (Surefire); `TestcontainersConfiguration` (public, `@ServiceConnection`,
`postgres:16-alpine`); `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` +
`@Import(...)` for adapters, co-located in the adapter package to reach package-private types;
`@SpringBootTest @AutoConfigureMockMvc` for HTTP slices. Notably: **no mocking framework** is used
anywhere — test doubles are lambdas and hand-rolled decorators. `SecurityAuthorizationTests` uses
**real minted tokens** from the live endpoint rather than `jwt()` post-processors, on the stated
grounds that a post-processor bypasses the machinery under test. Test principals are
`test-educator` / `test-learner` / `test-admin` (the `dev-` roster is rejected outside the dev
profile by design).

### 1.10 Known debt inherited (Step 3 review §7)
Integration tests share mutable database state with uniqueness kept by naming convention and no
per-test rollback — the review flags this as *"works today and grows more fragile with each suite
added."* Step 4 adds three suites. This step should improve it rather than compound it (§11).
Also open, and outside Step 4: ADR-001…010 texts pending transcription; one manual `java -jar`
run to convert the fail-fast inference into evidence.

### 1.11 Roadmap position
Learner Profiling is the last blocker on the analytical core. `gapanalysis` cannot begin without
`learner :: api`; `recommendation` sits downstream of that. Its output contract is load-bearing
twice over, which argues for publishing *less* rather than more in this step (§5, §13).

---

## 2. Required Domain Model

The module's charter is evidence ingestion **and** resolution — so the model has three layers, not
one: what was observed (evidence), what that means (a resolved proficiency), and whose it is
(the profile).

```
LearnerProfile                       ← aggregate root
  ├─ LearnerId              (identity, published in api)
  ├─ AuthSubject            (the JWT `sub` this profile belongs to — ADR-016/017)
  ├─ DisplayName            (minimal PII, §10)
  ├─ Instant createdAt
  └─ ProficiencyAssertion[] ← entities inside the aggregate, one per CompetencyId
        ├─ AssertionId
        ├─ CompetencyId               (cross-context reference by value only)
        ├─ CompetencyFrameworkId      (levels are framework-scoped — §2.1)
        ├─ AttainedLevel              (resolved: ordinal + level code)
        ├─ EvidenceRecord[]           (value objects; the basis of the resolution)
        └─ Instant resolvedAt
EvidenceRecord (VO)
  ├─ EvidenceType   {SELF_DECLARED, ASSESSMENT, COURSE_COMPLETION, CERTIFICATION, OBSERVATION}
  ├─ ClaimedLevel   (ordinal + code, as asserted by the source)
  ├─ Confidence     (bounded 0.0–1.0, or an ordinal band)
  ├─ String source  (free-text provenance, bounded length)
  └─ Instant recordedAt
```

**Domain service port:** `LevelResolutionPolicy` — given the evidence set for one competency,
produce the `AttainedLevel`. A port, not a method, for three reasons: it is the module's only
genuine business *policy* (everything else is bookkeeping); it is substitutable, which mirrors
ADR-006's `RecommendationStrategy` pattern and gives RQ3 a second, independent instance of the
same architectural technique in a different context; and it is the natural test seam, exactly as
`ConformanceValidator` was in Step 2. Default implementation: highest-confidence-wins with
recency as tie-breaker, one class, fully unit-tested.

### 2.1 Why assertions carry a framework id
`ProficiencyLevel` is an M1 element scoped to one framework model: level `L2` in a bespoke
curriculum framework and `L2` in SFIA are unrelated. An assertion that carried only a level code
would be uninterpretable outside its framework, and Gap Analysis compares learner level against
target level. The assertion therefore carries `CompetencyFrameworkId` plus both the ordinal
(comparable — what Gap Analysis needs) and the code (human-readable — what the API renders). This
is denormalisation into the learner context by design; ADR-011 forbids reaching across the schema
boundary to resolve it.

---

## 3. Aggregate Boundaries

**One aggregate: `LearnerProfile`, with `ProficiencyAssertion` as an internal entity.**

The invariant requiring a transactional boundary is *at most one current assertion per
(profile, competency)* — recording new evidence must either extend the existing assertion and
re-resolve it, or create the first one, and that decision cannot be made correctly by two
concurrent transactions. Holding assertions inside the aggregate makes the invariant enforceable
in code rather than hopefully enforceable by a unique constraint alone (though the constraint is
also present, as belt-and-braces, mirroring the Step 2 duplicate-framework pattern).

**The trade-off, stated honestly.** Recording one piece of evidence loads the whole profile.
Bounded by realistic size — a curriculum framework has tens to low hundreds of competencies, and
a learner will hold assertions for a subset — this is acceptable at dissertation scale and
measurable in W11. The documented escape hatch, should profiling ever load large: split
`ProficiencyAssertion` into its own aggregate keyed by `(LearnerId, CompetencyId)`, keeping the
uniqueness invariant on the database constraint alone. That is a contained change because the
repository port already mediates all access.

**Explicitly *not* aggregates:** `EvidenceRecord` is a value object inside its assertion — it has
no identity worth tracking independently and is never modified after recording, only superseded.

---

## 4. Value Objects

| VO | Rules |
|---|---|
| `AuthSubject` | Non-blank, trimmed, ≤ 200 chars. Opaque string — deliberately *not* parsed or validated as a username, so ADR-013's anticipated substitution (a real identity source supplying stable user ids) changes content without changing type. |
| `DisplayName` | Non-blank, trimmed, ≤ 200 chars. Mirrors `FrameworkName`'s constructor-validation idiom exactly. |
| `AttainedLevel` | `int ordinal` (≥ 0) + `String code` (non-blank, ≤ 50). Comparable by ordinal. The unit Gap Analysis will compare. |
| `Confidence` | Bounded `double` in [0.0, 1.0], rejecting NaN and out-of-range at construction. |
| `EvidenceType` | Closed enum, five constants. |
| `AssertionId` | UUID record implementing `shared.Identifier` — internal to the module, **not** published in `api`. |
| `EvidenceRecord` | Composite VO; all components non-null; `recordedAt` mandatory. |

All are records with compact-constructor validation and equality by value, matching the Step 2
pattern (`FrameworkName`, `FrameworkVersion`, `FrameworkDescriptor`). All framework-free.

---

## 5. Repository Ports

One port, in the domain, technology-blind:

```
LearnerProfileRepository
  LearnerProfile              save(LearnerProfile profile)
  Optional<LearnerProfile>    findById(LearnerId id)
  Optional<LearnerProfile>    findByAuthSubject(AuthSubject subject)
  boolean                     existsByAuthSubject(AuthSubject subject)
  List<LearnerProfileSummary> findAllSummaries()
```

`findByAuthSubject` is what makes `/api/learners/me` resolvable and is the mechanical consequence
of ADR-016 plus the `LearnerId` javadoc: the mapping lives here, at the application boundary, not
in `platform` and not in the token. `LearnerProfileSummary` is a content-free read model
(id, display name, assertion count, createdAt) so listings never load assertion graphs — the same
column-only-projection discipline that satisfied Step 2's performance gate.

### 5.1 Published contract (`learner.api`) — recommend deferring
**Recommendation: publish nothing new in Step 4 beyond the existing `LearnerId`.** Gap Analysis
does not exist yet; publishing a proficiency-snapshot contract now would freeze a shape guessed in
advance of its only consumer, and `gapanalysis` already declares the dependency, so the contract
becomes load-bearing the moment it exists. Step 5 should introduce it driven by actual need. The
alternative — publish a `ProficiencySnapshot` record now — is defensible and would let Step 5 start
faster; I record it as the rejected option with reasons rather than omitting it. Flagged for your
ratification (§13, R-4).

---

## 6. Application Services

**`LearnerProfileService`** — concrete class, no driving-port interface (the Step 2 YAGNI ruling,
still applicable: one caller, no second implementation). `@Transactional` boundary. Depends on
`LearnerProfileRepository`, `LevelResolutionPolicy` and `Clock` — **not** on anything in
`platform`, and not on `SecurityContextHolder`, which the strengthened fitness function now
enforces.

Every ownership-sensitive method takes the caller's subject as an explicit parameter, per ADR-016:

```
LearnerProfile createProfile(CreateLearnerProfileCommand command)
LearnerProfile recordEvidence(RecordEvidenceCommand command)
LearnerProfile getOwnProfile(AuthSubject caller)
LearnerProfile getProfileForReader(LearnerId id, AuthSubject caller, boolean callerMayReadAny)
List<LearnerProfileSummary> listProfiles()
```

**`callerMayReadAny` is the one design point deserving scrutiny.** Role interpretation belongs to
the platform; the learner module cannot see `Role`. Passing a resolved boolean keeps the
*decision* ("does an EDUCATOR see all profiles?") in the security layer while keeping the
*enforcement* ("this caller may see this resource") in the application layer where the resource is
in hand. The alternative — passing role strings inward — would smuggle the security vocabulary
across the boundary in all but name. This is the concrete form ADR-015's promised amendment takes.

**`LearnerModuleConfiguration`** — `@Configuration` instantiating the default
`LevelResolutionPolicy` as a bean, so the domain ring stays annotation-free (exactly as
`CompetencyModuleConfiguration` does for `ConformanceValidator`).

---

## 7. Command / Query Model

Framework-free records in `learner.application`, mirroring `RegisterFrameworkCommand`:

```
CreateLearnerProfileCommand(String authSubject, String displayName)
RecordEvidenceCommand(
        String  authSubject,        // ADR-016: from the token, never the request body
        UUID    competencyId,
        UUID    competencyFrameworkId,
        EvidenceType type,
        int     claimedOrdinal,
        String  claimedLevelCode,
        double  confidence,
        String  source)
```

Queries return domain read models (`LearnerProfile`, `LearnerProfileSummary`); the web adapter
maps to DTOs. No separate query bus — ADR-007 fixes CQRS scope to the Gap Analysis read
projection only, and inventing a second CQRS surface here would contradict it.

**The subject field is not user input.** DTOs must not carry it; the web mapper's signature takes
it as a separate argument supplied by the controller from the token. This is enforceable by test
(§11) and is the trade-off ADR-016 explicitly records.

---

## 8. REST Endpoints

| Verb | Path | Access | Semantics |
|---|---|---|---|
| `POST` | `/api/learners/me` | LEARNER (self) | Creates the caller's own profile; subject from token. `409` if one exists. |
| `GET` | `/api/learners/me` | any authenticated | The caller's own profile with assertions; `404` if none. |
| `POST` | `/api/learners/me/evidence` | LEARNER (self) | Records evidence, re-resolves the affected assertion, returns the updated profile. |
| `GET` | `/api/learners/{id}` | owner, or EDUCATOR/ADMIN | Ownership evaluated in the application layer. |
| `GET` | `/api/learners` | EDUCATOR/ADMIN | Summaries only; assertion graphs never loaded. |

**Provisioning is explicit, not implicit.** Auto-creating a profile on first `GET /me` would
perform a write on a read — surprising, and untestable as an idempotent GET. `POST /api/learners/me`
is the deliberate act.

Errors follow the Step 2 convention exactly: a module-local `@RestControllerAdvice(assignableTypes
= LearnerProfileController.class)` rendering RFC 9457 problem details — `404` not found, `409`
duplicate profile, `422` for a resolution/validation failure that is semantically (not
syntactically) invalid, `400` from Bean Validation on DTOs.

---

## 9. Database Design

> **SUPERSEDED BY ADR-020 (amendment A1).** The schema proposed in this section — in particular
> the `jsonb` evidence column — was **not** the design implemented. It is preserved unedited below
> as the record of what was proposed and considered; §9.1 states what was built instead and why.
> Read §9.1 before treating anything in this section as current.

`egas/src/main/resources/db/migration/learner/V200__create_learner_profile_tables.sql`
(directory to be created; the Flyway location is already wired).

```sql
create table learner.learner_profile (
    id            uuid         primary key,
    auth_subject  varchar(200) not null,
    display_name  varchar(200) not null,
    created_at    timestamptz  not null,
    constraint uq_learner_auth_subject unique (auth_subject)
);

create table learner.proficiency_assertion (
    id                      uuid         primary key,
    learner_profile_id      uuid         not null
        references learner.learner_profile (id) on delete cascade,   -- intra-schema FK: mandatory
    competency_id           uuid         not null,   -- cross-context: value only, no FK (ADR-011)
    competency_framework_id uuid         not null,
    attained_ordinal        integer      not null,
    attained_level_code     varchar(50)  not null,
    resolved_at             timestamptz  not null,
    evidence                jsonb        not null,   -- EvidenceRecord[]; see rationale
    constraint uq_assertion_profile_competency unique (learner_profile_id, competency_id)
);

create index ix_assertion_competency on learner.proficiency_assertion (competency_id);
```

Three decisions worth defending. **`uq_learner_auth_subject`** makes the one-profile-per-subject
invariant database-authoritative, with the adapter translating the constraint violation into a
domain exception — the TOCTOU-safe pattern proven in Step 2. **Evidence as `jsonb`** rather than a
third table: evidence is append-only, always read as a whole set in service of one assertion, never
queried independently, and its shape will evolve as evidence types are added — precisely the
profile that suits a document column, and consistent with ADR-005's use of jsonb where structure is
subordinate to the owning row. A third relational table would add a join to every profile load for
no query benefit. **`ix_assertion_competency`** anticipates Gap Analysis's access pattern
("who holds evidence for competency X") without waiting for it; it is one index, cheap, and
removable if unused.

No cross-schema foreign key to `competency.framework_model` — forbidden by ADR-011 and by the V1
migration header.

### 9.1 Amendment A1 — the implemented schema (ADR-020)

**What was proposed above.** Evidence as a `jsonb` array on `proficiency_assertion`, on the grounds
that evidence is append-only, always read as a whole set in service of one assertion, never queried
independently, and shaped to evolve — with a third relational table dismissed as adding a join for
no query benefit.

**What was decided instead.** ADR-020 places learner state in three relational tables —
`learner.profile`, `learner.proficiency_assertion`, `learner.evidence_record` — and confines
`jsonb` to dynamic model artefacts. The proposal was not wrong about evidence's access pattern; it
was displaced by three considerations it had not weighed:

1. **Invariant enforcement.** The aggregate's central rule is *at most one assertion per
   competency*, and `LearnerProfile`'s committed documentation states that a database constraint
   backs it as a second line of defence. `uq_assertion_profile_competency` delivers that. The
   equivalent inside a `jsonb` array — "no two elements share a `competency_id`" — is not
   expressible as a PostgreSQL constraint, so the proposed schema would have left shipped
   documentation promising a guarantee the database did not provide.
2. **Query requirements.** Gap Analysis (ADR-007) will ask which learners hold which competency.
   Against rows that is an indexed lookup; against documents it is a containment predicate over
   every profile. The same applies to `findAllSummaries`, where an assertion count is an aggregate
   query relationally and a document parse otherwise.
3. **Aggregate persistence needs.** Evidence rows carry no domain identity, so a document column
   hides the question of row identity entirely — and it is precisely that question which decides
   whether re-saving an existing profile updates in place or collides with itself. Making the rows
   explicit surfaced the problem where it could be constrained, indexed, and tested.

The distinguishing test ADR-020 records is **who defines the shape**: a competency framework's
structure is defined at runtime by its metamodel and admits no stable DDL, whereas a learner
profile's is fixed by domain types the compiler already checks. `jsonb` is right for the first and
unnecessary for the second.

**Implemented schema** (`V200__create_learner_profile_tables.sql`, committed): `profile`
(`uq_learner_auth_subject`); `proficiency_assertion` (`uq_assertion_profile_competency`, intra-schema
FK, `competency_id`/`framework_id` unkeyed per ADR-019); `evidence_record` (`uq_evidence_assertion_seq`,
`seq` for append order, `confidence numeric(4,3)`), plus `CHECK` constraints mirroring the
`Confidence` and `AttainedLevel` value objects. The proposed `ix_assertion_competency` index was
**deferred**, following V100's precedent of adding an index when a predicate exists rather than in
anticipation of one; ADR-020's Future Evolution records it as the change Gap Analysis will make.

---

## 10. Security Implications

1. **Ownership authorisation is the point of this step.** ADR-015's amendment must be written, not
   merely implied. Coarse rules stay in the filter chain; the ownership predicate lives in
   `LearnerProfileService` where the resource is in hand.
2. **New filter-chain rules** (ordered, inserted before the terminal
   `anyRequest().authenticated()`):
   `GET /api/learners` → `hasAnyRole(EDUCATOR, ADMIN)`; everything else under `/api/learners/**`
   → `authenticated()`, with ownership decided in the application layer. Order is semantically
   significant, so the list rule must precede the general one — asserted cell-by-cell in tests
   rather than trusted.
3. **Existence disclosure — recommend `404`, not `403`.** If a LEARNER requests another learner's
   profile by id, returning `403` confirms the profile exists; returning `404` does not. I
   recommend `404` for non-permitted access to an existing resource, making present-and-forbidden
   indistinguishable from absent. This trades a small diagnostic clarity for eliminating an
   enumeration oracle over learner identifiers, and it is the kind of decision an examiner will
   notice. Flagged for ratification (§13, R-3).
4. **Subject provenance.** The subject must reach the command only from `@AuthenticationPrincipal
   Jwt`. A DTO field named `authSubject` would be an authorisation hole reachable from the request
   body; the DTOs in §12 deliberately have no such field, and a test asserts that supplying one is
   ignored.
5. **PII and GDPR.** Learner profiles are personal data under GDPR, which matters for a University
   of Limerick dissertation even at prototype scale. The model deliberately holds the minimum:
   a display name and an opaque subject. Free-text `source` on evidence is bounded and documented
   as not for personal data. No email, no date of birth, no institutional identifiers. Erasure is
   supported structurally by `on delete cascade`, though a delete endpoint is **out of scope** for
   Step 4 and should be recorded as such rather than silently omitted.
6. **Unchanged posture.** Deny-by-default terminal rule, stateless sessions, CSRF rationale, bearer
   entry point and access-denied handler — all inherited untouched. No new permits.

---

## 11. Testing Strategy

Target: **~45 new tests, suite ≈ 137**, all green on real PostgreSQL 16.

| Tier | Class | ≈ | Establishes |
|---|---|---|---|
| Domain unit | `LearnerProfileAggregateTests` | 8 | Profile creation; first evidence creates an assertion; further evidence extends and re-resolves it; one-assertion-per-competency invariant; ownership predicate on the aggregate; identity equality; fixed `Clock` timestamps |
| Domain unit | `LearnerValueObjectTests` | 5 | Construction validation and normalisation for `AuthSubject`, `DisplayName`, `AttainedLevel`, `Confidence` bounds (incl. NaN) |
| Domain unit | `LevelResolutionPolicyTests` | 5 | Highest-confidence-wins; recency tie-break; single-evidence case; empty-evidence refusal; substitutability via a lambda policy |
| Application | `LearnerProfileServiceTests` | 7 | **Ownership decided correctly with no security infrastructure present** — the ADR-016 payoff, asserted directly; duplicate-profile rejection; reader-may-read-any path; not-found path |
| Persistence | `JpaLearnerProfileRepositoryTests` | 6 → **11 delivered (A1)** | `@DataJpaTest` on real PostgreSQL: field-by-field aggregate round-trip over **relational** evidence rows (ADR-020, not jsonb); update round-trip proving evidence accumulates rather than colliding; evidence ordering; `AssertionId` persistence; confidence precision; `findByAuthSubject`; `existsByAuthSubject`; unique-constraint → domain exception; the constraint rejecting a duplicate assertion inserted directly; summaries as a projection loading zero entities |
| Web/API | `LearnerProfileApiTests` | 9 | `/me` create → read → record-evidence cycle; `404` on absent; `409` on duplicate; `400` on malformed; assertion rendering incl. resolved level; **a request body carrying `authSubject` is ignored** |
| Security | `LearnerProfileOwnershipTests` | 7 | The ownership matrix with **real minted tokens** (the Step 3 convention): learner reads own → 200; learner reads other → 404; educator reads any → 200; admin reads any → 200; learner lists → 403; educator lists → 200; no token → 401 + `WWW-Authenticate: Bearer` |
| Architecture | unchanged | 9 | Seven fitness functions plus two Modulith verifications pass **unmodified** — including the security rule that would fail on a `SecurityContextHolder` read |

Conventions carried over deliberately: `*Tests` naming; no mocking framework (lambdas and
hand-rolled doubles); real tokens over `jwt()` post-processors; `LearnerFixtures` as a shared public
builder mirroring `FrameworkFixtures`.

**One inherited-debt improvement.** The Step 3 review flags shared mutable database state across
integration suites as growing more fragile with each suite added; Step 4 adds three. Rather than
compound it, this step should adopt a per-class cleanup (`@Sql` truncate of `learner.*` before each
class, or an explicit teardown), scoped to the new suites only so no existing test changes
behaviour. Small, contained, and it converts a recorded fragility into a resolved one.

---

## 12. Exact File Manifest

Paths are **repository-root-relative** — note the `egas/` prefix on all source paths.

### Created — production (24)
```
egas/src/main/java/ie/ul/egas/learner/domain/LearnerProfileRepository.java
egas/src/main/java/ie/ul/egas/learner/domain/model/LearnerProfile.java
egas/src/main/java/ie/ul/egas/learner/domain/model/ProficiencyAssertion.java
egas/src/main/java/ie/ul/egas/learner/domain/model/AssertionId.java
egas/src/main/java/ie/ul/egas/learner/domain/model/AuthSubject.java
egas/src/main/java/ie/ul/egas/learner/domain/model/DisplayName.java
egas/src/main/java/ie/ul/egas/learner/domain/model/AttainedLevel.java
egas/src/main/java/ie/ul/egas/learner/domain/model/Confidence.java
egas/src/main/java/ie/ul/egas/learner/domain/model/EvidenceRecord.java
egas/src/main/java/ie/ul/egas/learner/domain/model/EvidenceType.java
egas/src/main/java/ie/ul/egas/learner/domain/model/LearnerProfileSummary.java
egas/src/main/java/ie/ul/egas/learner/domain/model/LearnerProfileNotFoundException.java
egas/src/main/java/ie/ul/egas/learner/domain/model/DuplicateLearnerProfileException.java
egas/src/main/java/ie/ul/egas/learner/domain/policy/LevelResolutionPolicy.java
egas/src/main/java/ie/ul/egas/learner/domain/policy/HighestConfidenceResolutionPolicy.java
egas/src/main/java/ie/ul/egas/learner/domain/policy/UnresolvableEvidenceException.java
egas/src/main/java/ie/ul/egas/learner/application/LearnerProfileService.java
egas/src/main/java/ie/ul/egas/learner/application/CreateLearnerProfileCommand.java
egas/src/main/java/ie/ul/egas/learner/application/RecordEvidenceCommand.java
egas/src/main/java/ie/ul/egas/learner/application/LearnerModuleConfiguration.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/persistence/LearnerProfileJpaEntity.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/persistence/ProficiencyAssertionJpaEntity.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/persistence/EvidenceRecordJpaEntity.java  (A1: added — evidence is a table under ADR-020, not a jsonb column)
egas/src/main/java/ie/ul/egas/learner/infrastructure/persistence/LearnerProfileSpringDataRepository.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/persistence/JpaLearnerProfileRepository.java
```
Web adapter (7):
```
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/LearnerProfileController.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/LearnerProfileExceptionHandler.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/LearnerProfileWebMapper.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/dto/CreateLearnerProfileRequest.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/dto/RecordEvidenceRequest.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/dto/LearnerProfileResponse.java
egas/src/main/java/ie/ul/egas/learner/infrastructure/web/dto/LearnerProfileSummaryResponse.java
```
Migration (1):
```
egas/src/main/resources/db/migration/learner/V200__create_learner_profile_tables.sql
```

### Created — tests (8)
```
egas/src/test/java/ie/ul/egas/learner/LearnerFixtures.java
egas/src/test/java/ie/ul/egas/learner/LearnerProfileAggregateTests.java
egas/src/test/java/ie/ul/egas/learner/LearnerValueObjectTests.java
egas/src/test/java/ie/ul/egas/learner/LevelResolutionPolicyTests.java
egas/src/test/java/ie/ul/egas/learner/LearnerProfileServiceTests.java
egas/src/test/java/ie/ul/egas/learner/LearnerProfileApiTests.java
egas/src/test/java/ie/ul/egas/learner/LearnerProfileOwnershipTests.java
egas/src/test/java/ie/ul/egas/learner/infrastructure/persistence/JpaLearnerProfileRepositoryTests.java
```

### Created — documentation (4)
```
docs/adr/017-learner-identity-mapping-and-provisioning.md
docs/adr/018-evidence-model-and-level-resolution.md
docs/adr/019-cross-context-reference-integrity.md
docs/diagrams/learner-module-internal.puml
```

### Modified (3)
```
egas/src/main/java/ie/ul/egas/platform/security/SecurityConfig.java   (learner rules only)
docs/adr/015-centralised-authorisation-rules.md                       (promised amendment)
docs/adr/README.md                                                    (index rows 017–019)
```

### Explicitly NOT modified
`pom.xml` (no new dependency), `application.yml` (Flyway learner location already wired),
`HexagonalArchitectureTests.java`, `ModularityTests.java`, `learner/package-info.java`
(`allowedDependencies` unchanged), and **everything under `egas/src/main/java/ie/ul/egas/competency/`**
— the zero-touch criterion, verified by empty diff, exactly as Step 3 established.

---

## 13. Risks, Trade-offs, and Decisions Requiring Ratification

**Decisions I need ratified before implementation:**

| # | Decision | Recommendation |
|---|---|---|
| R-1 | **Identity mapping** (ADR-017): profile holds `AuthSubject` as an attribute with a unique constraint, vs. a separate subject→LearnerId mapping table | Attribute + unique constraint. One table, one invariant, and the `LearnerId` javadoc's "application-layer concern" is satisfied by the repository lookup rather than by a second persistence structure. |
| R-2 | **Step 4 scope**: evidence + resolution policy, vs. self-declared levels only | Include evidence and the policy port. The module's own javadoc names both, and the policy gives RQ3 a second instance of substitutable-strategy architecture outside Recommendation. **Cut-line if the step over-runs:** ship `SELF_DECLARED` evidence only, keeping the port. |
| R-3 | **Existence disclosure**: `404` vs `403` for a learner requesting another's profile | `404`. Removes an enumeration oracle at negligible cost. |
| R-4 | **`learner.api` publication**: publish nothing new now, vs. publish a `ProficiencySnapshot` for Step 5 | Publish nothing. `gapanalysis` already depends on this package; a contract guessed before its consumer exists is a contract frozen wrong. |
| R-5 | **Competency reference validation** (ADR-019): accept unvalidated `CompetencyId`, vs. add a published query port to `competency.api` | Accept unvalidated. Adding a port breaks zero-touch on the frozen module and creates a synchronous cross-module call the eventual event-driven projection (ADR-007) is designed to avoid. Record explicitly as a deliberate consistency choice, not an oversight. |

**Risks:**

| Risk | L/I | Mitigation |
|---|---|---|
| Ownership check placed in the filter chain by habit, silently widening access | Med/High | ADR-015 amendment written *before* the web phase; the ownership matrix asserts every cell with real tokens |
| Subject leaking in from a request body | Low/High | DTOs have no such field; an explicit test asserts a supplied `authSubject` is ignored |
| Filter-chain rule ordering error (list rule after the general rule) | Med/Med | Order-sensitive cells asserted individually; the inherited failure mode ADR-015 already records |
| ~~jsonb evidence mapping friction (Hibernate `@JdbcTypeCode`, list serialisation)~~ — **retired by A1**: ADR-020 chose relational evidence rows, so this risk cannot arise | — | — |
| **(A1, materialised)** Evidence row identity on re-save: rows carry no domain identity, so generated ids differ on every save and a merge inserts a replacement set colliding with its own orphans | Med/**High** | Row ids derived from `(assertionId, seq)` rather than generated, making a re-save an update in place; **caught in review by measurement, not by the suite** — the update round-trip test that now guards it was added because no test exercised a second save. Valid only while ADR-018 keeps evidence append-only |
| **(A1)** Two persistence idioms coexist (relational learner, jsonb competency) and read as inconsistent without the rationale | Low/Low | ADR-020 records the shape-definition test that distinguishes them; §9.1 cross-references it |
| Aggregate load cost as assertions accumulate | Low/Med | Bounded by realistic framework size; measured in W11; documented escape hatch in §3 |
| Shared mutable test state compounding (inherited) | Med/Med | Per-class cleanup for the new suites (§11) |
| Scope creep into assessment/grading features | Med/Med | R-2's cut-line is contractual; delete endpoint and evidence revision explicitly out of scope |

---

## 14. Implementation Phases

Mirroring the cadence that worked in Steps 2 and 3, and the sequence the Step 3 review recommends.
Each phase restates applicable ADRs, constraints preserved, and acceptance criteria before code;
each ends with verification and a stop-and-wait gate.

| Phase | Content | Gate |
|---|---|---|
| **0 — Decisions** | ADR-017, ADR-018, ADR-019 authored and Accepted; ADR-015 amendment drafted; index updated. No code. | Ratification of R-1…R-5 |
| **1 — Domain** | Aggregate, entity, six value objects, exceptions, repository port, resolution policy + default impl. Framework-free. | `mvn verify` green; +18 tests (≈110) |
| **2 — Persistence** | `V200__`, **three** JPA entities (A1: `evidence_record` became a table under ADR-020, not a `jsonb` column), Spring Data internals, port adapter with constraint translation and derived evidence row ids. | **Delivered** at `ab1df7f`: green; **+11 (127 actual**, against ≈116 projected); `ddl-auto: validate` passes; zero-touch under `competency/src/main` held |
| **3 — Application** | Service, two commands, module configuration. **Ownership logic proven with zero security infrastructure** — the ADR-016 demonstration. | Green; +7 (≈123) |
| **4 — Web & authorisation** | Controller, mapper, advice, four DTOs; `SecurityConfig` learner rules; ADR-015 amendment committed. | Green; +16 (≈139); nine architecture tests unchanged; empty diff under `competency/src/main` |
| **5 — Documentation & evidence** | `learner-module-internal.puml`; evidence capture (ownership matrix transcripts, Swagger cycle); Step 4 completion review. | DoD met; step report; stop |

**Definition of Done (draft, to be finalised at Phase 0):** `mvn verify` BUILD SUCCESS with ≈137+
tests and zero failures on real PostgreSQL; ownership matrix proven cell-by-cell with real tokens;
`404` non-disclosure verified; nine architecture tests pass unmodified; empty `git diff` under
`egas/src/main/java/ie/ul/egas/competency/`; `learner` module `allowedDependencies` unchanged;
ADR-017/018/019 Accepted and ADR-015 amended; module diagram committed; step report delivered;
stop-and-wait observed.

---

*Inspection only. No implementation is authorised by this document. Recommended repository location
once approved: `docs/planning/step4-plan.md`.*
