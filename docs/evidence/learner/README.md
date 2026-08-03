# Step 4 learner evidence pack

Four artefacts supporting the Step 4 Definition of Done and the dissertation's RQ2 and RQ3 claims.

| # | Artefact | File | How it is produced | Present |
|---|----------|------|--------------------|---------|
| 1 | Ownership authorisation matrix | `01-ownership-matrix.md` | `capture-evidence.sh` | generated on demand |
| 2 | Anti-enumeration (404 ≠ 403) | `02-anti-enumeration.md` | `capture-evidence.sh` | generated on demand |
| 3 | Evidence recording and resolution | `03-evidence-recording-cycle.md` | `capture-evidence.sh` | generated on demand |
| 4 | Zero-touch modularity across two steps | `04-zero-touch-modularity.md` | `capture-evidence.sh` (no instance needed) | generated on demand |

Nothing here is written by hand. Following the precedent set by the Step 3 security pack, each
artefact is produced by executing the system or interrogating the repository, so it cannot drift
away from the behaviour it documents. A claim that can be generated should never be asserted.

## Regenerating

```bash
cd egas && mvn spring-boot:run
```

`mvn spring-boot:run` activates the `dev` profile, which supplies the development principals and
permits the generated in-memory JWT keypair (ADR-013, amendment A5). PostgreSQL starts via
`compose.yaml`.

Then, from the repository root:

```bash
./docs/evidence/learner/capture-evidence.sh
```

Artefact 04 is produced first and needs no running instance — it reads git history only, so the
modularity evidence regenerates even without a database.

## What each artefact establishes

**01** walks every cell of ADR-015 Amendment 1: owner, educator and admin read a profile by
identifier; a learner is refused the full listing with `403`; an unauthenticated caller gets `401`.
The `403` on row 4 is deliberate contrast material for artefact 02 — refusing to list *every*
profile discloses nothing about any particular one, so a role answer is safe there and unsafe on
an individual resource.

**02** is the security property of the step. An existing-but-forbidden profile and a non-existent
identifier return responses differing only in the RFC 9457 `instance` field, which echoes the URI
the caller supplied. A `403` would have confirmed that an identifier names a real profile and made
the endpoint an enumeration oracle. Asserted mechanically in `LearnerProfileApiTests`; captured
here for the dissertation's security section.

**03** shows two observations collapsing into one resolved level with both records retained — the
provenance that makes a downstream skill gap explainable rather than merely reported (RQ3).

**04** is the RQ2 modularity result: an entire bounded context added across two steps with an empty
diff under `competency/src/main`, and the `learner` module's `allowedDependencies` unchanged. It
also reports how much *was* added, so the reader can weigh the claim against the work it covers.

## Development credentials

The principals used (`dev-learner`, `dev-educator`, `dev-admin`) live in
`egas/src/main/resources/application-dev.yml` with their plaintext passwords documented there.
Spring loads that file only under the `dev` profile, and a `dev-`prefixed principal outside that
profile aborts startup. They are deliberately non-secret; no production credential belongs in this
repository.
