# EGAS Fitness-Function Verification Report

**Scope:** the nine automated architecture checks, as at Step 5 completion (commit `1fe679d`)
**Suite:** `mvn clean verify` — 293 tests, 0 failures, 0 errors, 0 skipped
**Governing decision:** ADR-008 (boundary enforcement: Spring Modulith verification + ArchUnit
fitness functions)

---

## 1. Why this report exists

The dissertation's RQ2 claim is that architectural boundaries in EGAS are *enforced* rather than
*documented*. A passing test suite is weak evidence for that on its own: a rule that no code could
violate passes exactly as loudly as a rule that catches real defects, and an architecture test with
an empty selection is the classic silent failure.

This report therefore records, for each of the nine checks, three things: what it forbids, what
would break it, and — where such evidence exists — the occasion on which it was **observed to fail
against a deliberately broken tree**. The third column is the one that distinguishes a fitness
function from a comment.

Nothing here is a new check. The count has been nine since Step 3 and is nine now.

---

## 2. The nine checks

Seven ArchUnit rules in `HexagonalArchitectureTests` and two Spring Modulith verifications in
`ModularityTests`.

| # | Check | Location | Enforces |
|---|-------|----------|----------|
| 1 | `domainIsFrameworkFree` | `HexagonalArchitectureTests` | No `..domain..` class depends on Spring, JPA, Servlet, Hibernate or Jackson |
| 2 | `domainDoesNotReachOutwards` | `HexagonalArchitectureTests` | No `..domain..` class depends on `..application..` or `..infrastructure..` |
| 3 | `applicationStaysOutOfAdapters` | `HexagonalArchitectureTests` | No `..application..` class depends on infrastructure, Spring Web, Spring Security, Servlet or JPA |
| 4 | `restControllersOnlyInWebAdapters` | `HexagonalArchitectureTests` | Every `@RestController` resides in `..infrastructure.web..` |
| 5 | `publishedContractsArePure` | `HexagonalArchitectureTests` | `..api..` depends only on `java..`, `shared..`, other `..api..`, `org.springframework.modulith..` |
| 6 | `emfConfinedToCompetencyModule` | `HexagonalArchitectureTests` | No class outside `competency..` depends on `org.eclipse.emf..` / `emfcloud..` |
| 7 | `emfSerializationStaysOutOfDomain` | `HexagonalArchitectureTests` | No `competency.domain..` class depends on EMF XMI or emfjson |
| 8 | `moduleTopologyIsValid` | `ModularityTests` | Declared `allowedDependencies` hold; no undeclared dependency; no cycle |
| 9 | `generateArchitectureDocumentation` | `ModularityTests` | The module canvas renders, which fails if the topology cannot be resolved |

`@AnalyzeClasses(importOptions = DoNotIncludeTests.class)` restricts checks 1–7 to production code.
The Step-1 `failOnEmptyShould=false` override has been removed, so a rule whose selection matches
nothing fails the build — the typo protection that keeps a vacuous rule from passing quietly.

---

## 3. Verification evidence

"Observed failing" means the tree was deliberately broken, the suite was run, the named test failed,
and the change was then reverted and the suite re-run green. Where a check has no such record, this
report says so rather than implying one.

| # | Check | Verified by | Observed failing? |
|---|-------|-------------|-------------------|
| 1 | `domainIsFrameworkFree` | Step 5 Phase 4a | **Yes.** `@Immutable` (Hibernate) added to `GapReportSummary`, a `domain.model` record. Rule failed with a violation naming the class; reverted, green |
| 2 | `domainDoesNotReachOutwards` | Selection non-empty across seven modules | No deliberate break recorded |
| 3 | `applicationStaysOutOfAdapters` | Strengthened by ADR-016 to include `org.springframework.security..` | No deliberate break recorded |
| 4 | `restControllersOnlyInWebAdapters` | ADR-014 was written *because* this rule constrained where the platform token endpoint could live | Not broken deliberately; it shaped a decision instead, which is the stronger outcome |
| 5 | `publishedContractsArePure` | Constrained three Step 5 contract designs (below) | No deliberate break recorded |
| 6 | `emfConfinedToCompetencyModule` | Load-bearing for Step 5: it is why model compilation happens inside Competency Modelling | No deliberate break recorded |
| 7 | `emfSerializationStaysOutOfDomain` | Selection non-empty within `competency.domain` | No deliberate break recorded |
| 8 | `moduleTopologyIsValid` | Passed unmodified while `gapanalysis` gained two inbound contracts | No deliberate break recorded |
| 9 | `generateArchitectureDocumentation` | **Yes, historically.** Failed in Step 4 when an `EvidenceRecord` javadoc containing an escaped quote broke Boot's `BasicJsonParser` during canvas generation; isolated by bisecting `javadoc.json` and fixed by rewording | Yes |

Two of the nine have been observed failing against a broken tree. That is honest rather than
impressive, and it is the number this report exists to state accurately.

---

## 4. Where a rule changed the design rather than caught a defect

The more interesting evidence is not a rule catching a mistake but a rule making a mistake
unavailable. Four occasions are on record.

**Check 4 produced ADR-014.** The platform module has no domain and no application ring, so the
Step 3 token endpoint had nowhere rule-satisfying to live. The alternatives were to exempt platform
from the rule — after which every later exception would argue from the precedent — or to build empty
rings around one controller. Neither was taken: platform adopted the adapter-placement convention
alone, and the rule passes *by construction*. A rule with exceptions stops being evidence.

**Check 5 shaped every published contract in Step 5.** `AttainedCompetency.type` is a `String`
rather than Learner Profiling's `EvidenceType` enum, and `LearnerIdentityQuery.learnerIdFor` takes a
`String` rather than the `AuthSubject` value object, because both of those are domain types this
rule forbids in an `api` package. The flattening is a real cost — one lost compile-time check on the
consumer's side — accepted so the boundary stays honest.

**Check 6 decided where model compilation happens.** ADR-012 confines EMF to Competency Modelling,
so a snapshot of an M1 graph could not be assembled inside Gap Analysis even if that were more
convenient. The compiler is therefore package-private in `competency.application`, and what crosses
the boundary is flat records. This rule is why the projection exists in the shape it does.

**Check 3 was strengthened rather than worked around.** ADR-016 found that an application service
calling `SecurityContextHolder` would have passed all nine checks as then written, so the rule gained
`org.springframework.security..`. Step 5 Phase 4a strengthened check 1 the same way, with
`org.hibernate..`, after Phase 2 and 4a introduced ORM-specific mapping beside a domain ring that
must stay pure. Both are amendments to existing rules; the count stayed at nine on both occasions.

---

## 5. What the fitness functions do *not* enforce

Recording the boundary of the enforcement claim matters as much as recording the claim.

- **They do not check documentation.** Three times during Steps 3–4 an Accepted ADR cited an
  artefact that did not exist. `docs/check-doc-references.sh` closes the cheaply-closable part of
  that gap — repository paths and test-class names in backticks — and nothing else. It is not one of
  the nine.
- **They do not check runtime behaviour.** No fitness function can tell that ownership is enforced,
  that absence survives a round trip, or that severity is not recomputed on load. Those are proven
  by tests, several of them verified by mutation; see the Step 5 completion review.
- **They do not check the database.** ADR-011's prohibition on cross-schema foreign keys is enforced
  by migration review, not by a rule. A migration adding one would pass all nine checks.
- **They do not check cardinality of module APIs.** Nothing stops an `api` package growing without
  limit, so long as what it contains is pure.
- **Checks 1–7 exclude test sources.** A test may legitimately construct domain objects alongside
  Spring infrastructure, and forbidding that would make integration testing impossible.

---

## 6. Reproducing

```bash
cd egas && mvn -Dtest='HexagonalArchitectureTests,ModularityTests' test
```

Expected: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` across the two classes. The full suite
runs them alongside everything else:

```bash
cd egas && mvn clean verify
```

Requires Docker — `ModularityTests` boots a Spring context, and the wider suite runs against real
PostgreSQL 16 via Testcontainers.
