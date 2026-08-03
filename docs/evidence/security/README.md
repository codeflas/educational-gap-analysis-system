# Step 3 security evidence pack

Four artefacts required by the Step 3 Definition of Done (§8.1 of the Step 3 plan). They double
as figures for the dissertation's security section.

| # | Artefact | File | How it is produced | Present |
|---|----------|------|--------------------|---------|
| 1 | JWT issuance flow | `01-jwt-issuance-flow.md` | `capture-evidence.sh` | captured (regenerable) |
| 2 | Swagger bearer authorisation | `02-swagger-bearer-authorization.png` | manual screenshot | **captured** |
| 3 | 401 authentication failure | `03-401-authentication-failure.md` | `capture-evidence.sh` | captured (regenerable) |
| 4 | 403 authorisation failure | `04-403-authorization-failure.md` | `capture-evidence.sh` | captured (regenerable) |

All four artefacts are committed. The three transcripts carry the capture timestamp of the run
that produced them and can be regenerated at any time by the instructions below; the screenshot
is a one-off manual capture.

Nothing in this directory is fabricated. The three transcripts are regenerated from a live
instance rather than committed as prose, so they cannot drift away from the system's actual
behaviour; the screenshot is genuinely manual and is marked missing until taken.

## Regenerating the transcripts

```bash
cd egas && mvn spring-boot:run
```

`mvn spring-boot:run` activates the `dev` profile, which is the only profile permitting the
generated in-memory JWT keypair (ADR-013, amendment A5). The packaged jar has no default profile,
so `java -jar` still refuses to start without real key material — the fail-fast policy is intact
where it matters. Postgres starts automatically via `compose.yaml`.

Then, from the repository root:

```bash
./docs/evidence/security/capture-evidence.sh
```

The script writes artefacts 01, 03 and 04. It never reads or prints the private key. Token
signatures do appear in the transcripts, which is safe: a signature is public data, unlike the key
that produced it. Because dev keys are regenerated on every restart, tokens in an older transcript
will not verify against a later instance — expected, and worth noting when comparing captures.

## Capturing artefact 02 (manual)

1. Start the instance as above and open <http://localhost:8080/swagger-ui.html>.
2. `POST /auth/token` with `{"username":"dev-educator","password":"dev-educator-password"}` and
   copy the `access_token` value from the response.
3. Click **Authorize**, paste the token (Swagger UI adds the `Bearer` prefix), and confirm.
4. Screenshot the Authorize dialog with the token applied, then run one authorised
   *Try it out* — `POST /api/frameworks` returning 201, or `GET /api/frameworks` returning 200 —
   and include that result in the same capture.
5. Save as `02-swagger-bearer-authorization.png` in this directory.

## Credentials

The development principals (`dev-educator`, `dev-learner`, `dev-admin`) and their BCrypt hashes
live in `egas/src/main/resources/application-dev.yml`, with the plaintext passwords documented
there in a comment. Spring loads that file only under the `dev` profile, so the roster cannot
reach a deployed instance; as a second line of defence, a principal whose username carries the
reserved `dev-` prefix aborts startup outside the profile. Both mechanisms exist because these
credentials are committed and therefore public knowledge.

They are deliberately non-secret and exist only for development; ADR-013 records identity
persistence as intentionally outside Step 3 scope, and any real deployment supplies its own roster
through the environment. No production credential belongs in this repository, hashed or otherwise.
