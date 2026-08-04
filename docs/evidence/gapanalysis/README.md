# Step 5 gap-analysis evidence pack

Five artefacts supporting the Step 5 Definition of Done and the dissertation's RQ2 and RQ3 claims.

| # | Artefact | File | How it is produced | Present |
|---|----------|------|--------------------|---------|
| 1 | Explainability chain, end to end | `01-explainability-chain.md` | `capture-evidence.sh` | generated on demand |
| 2 | Absence is not zero | `02-absence-is-not-zero.md` | `capture-evidence.sh` | generated on demand |
| 3 | Ownership authorisation matrix | `03-ownership-matrix.md` | `capture-evidence.sh` | generated on demand |
| 4 | Non-disclosure (404 ≠ 403) | `04-non-disclosure.md` | `capture-evidence.sh` | generated on demand |
| 5 | Integration cost across three steps | `05-integration-cost.md` | `capture-evidence.sh` (no instance needed) | generated on demand |

Nothing here is written by hand. Following the precedent set by the Step 3 security pack and the
Step 4 learner pack, each artefact is produced by executing the system or interrogating the
repository, so it cannot drift away from the behaviour it documents. A claim that can be generated
should never be asserted.

## Regenerating

```bash
cd egas && mvn spring-boot:run
```

`mvn spring-boot:run` activates the `dev` profile, which supplies the development principals and
permits the generated in-memory JWT keypair (ADR-013, amendment A5). PostgreSQL starts via
`compose.yaml`.

Then, from the repository root:

```bash
./docs/evidence/gapanalysis/capture-evidence.sh
```

Artefact 05 is produced first and needs no running instance — it reads git history only, so the
integration-cost evidence regenerates even without a database.

The script registers its own framework (`Evidence Framework 1.0`), provisions two profiles and
records one observation, so it is self-sufficient on a fresh instance. Re-running it is safe:
`uq_framework_name_version` rejects the duplicate registration and the script falls back to the
framework already present.

## What each artefact establishes

**01** is the RQ3 result. Every finding carries the analysis target it was measured against, the
attainment the learner was held to have reached, and the observations behind that attainment —
having crossed two module boundaries intact. It also shows what is *not* there: SE-ARC is absent
from the findings entirely, because the model describes it at no level and inventing a target would
fabricate a requirement the metamodel does not state.

**02** is the distinction ADR-021 exists to protect, shown surviving three layers independently —
domain, schema and wire. "Nothing has been measured" and "measured and far behind" call for
different remedies, and a system that collapsed them would send a recommender to propose learning
where an assessment is what is missing.

**03** walks every cell of the ADR-015 Amendment 2 matrix. The two refusal rows are the point: the
same unprivileged caller receives `404` for a report read by identifier and `403` for a
learner-scoped listing, because in the first case a lookup happened and in the second the identifier
was never resolved. That asymmetry is a decision, not an inconsistency.

**04** is the security property of the step. An existing-but-forbidden report and a non-existent
identifier return responses differing only in the RFC 9457 `instance` member, which echoes the URI
the caller supplied. Asserted mechanically as a byte comparison in `GapReportApiTests`; captured
here for the dissertation's security section.

**05** is the RQ2 measurement, and it is the artefact that most needs reading in context. Steps 3
and 4 show an empty diff under `competency/src/main`; Step 5 shows a small, purely additive one.
That is not a regression — it is the question the earlier steps could not answer, since a module
nobody consumes is never asked to serve anyone.

## A caveat worth stating

**Eventual consistency is visible here.** Projection is asynchronous (ADR-007), so a
`POST /api/gap-reports` issued in the seconds after a framework is registered answers `422 Competency
model unavailable`. That is correct behaviour, not a flake: the report says the model is not
available *yet* and names both possible causes rather than guessing. If artefact 01 or 02 comes back
with a 422 body, wait a moment and re-run — the projection listener has not committed.

## Development credentials

The principals used (`dev-educator`, `dev-learner`, `dev-admin`) live in
`egas/src/main/resources/application-dev.yml` with their plaintext passwords documented there.
Spring loads that file only under the `dev` profile, and a `dev-`prefixed principal outside that
profile aborts startup. They are deliberately non-secret; no production credential belongs in this
repository.

**Why the report is owned by an educator.** The ownership matrix needs an unprivileged caller who is
*not* the owner, and the dev roster contains only one LEARNER. Rather than invent a second
principal, artefact 03 has `dev-educator` own the report and `dev-learner` play the intruder, with
`dev-admin` demonstrating privileged access. The mechanically-asserted version of the same matrix in
`GapReportApiTests` does use two distinct LEARNER principals, because the test roster in
`egas/src/test/resources/application.properties` defines a second one for exactly this purpose.
