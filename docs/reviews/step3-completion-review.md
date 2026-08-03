# EGAS Architecture Review — Step 3 Completion

**Project:** Educational Gap Analysis System (EGAS) — Architectural Design of a Model-Driven Educational Gap Analysis System with AI-Based Recommendation Support

**Student:** Shubham Digamber Biradar (25322206) · **Supervisor:** Dr. Salim Saay · University of Limerick, M.Sc. Software Engineering

**Review date:** 3 August 2026

**Baseline under review:** Step 3 — Authentication & Authorisation (realisation of ADR-010), commits `dae74bf` … `78a80c3`, plus Phase 0 hardening

**Verified build evidence:** `mvn clean verify` → BUILD SUCCESS · Tests run: 92 · Failures: 0 · Errors: 0 · Skipped: 0 · executed against PostgreSQL 16 via Docker/Testcontainers with Flyway migrations applied (87 at Step 3 closure; 92 including Phase 0 hardening)

> *Formatting note.* The Step 2 review was committed with backslash-escaped Markdown (`\#`, `\*\*`), which renders literally. This review follows that document's structure and voice but writes plain Markdown, on the view that the escaping was a transcription artefact rather than a house style.

---

## 1. Executive Summary

Step 3 turned the Step 1 deny-by-default shell into a complete, standards-anchored security capability: a token endpoint issuing RS256 JWTs to configuration-backed development principals, a Spring OAuth2 resource server validating them statelessly, a three-role authorisation matrix over the existing registry, correct 401-versus-403 semantics, and an interactive Swagger client. Every claim in that sentence is asserted by an automated test.

The step's significance for the dissertation is not the security features themselves — self-issued JWTs are unremarkable engineering — but what their arrival demonstrated about the architecture. **The entire capability landed with zero changes under `competency/src/main`.** `git diff 6c847ca..78a80c3 -- egas/src/main/java/ie/ul/egas/competency/` is empty. A cross-cutting concern that in a conventional layered system would have touched every controller instead touched none, because authorisation was expressible at one choke point and the module boundaries held. That is mechanically checkable evidence for RQ2, not an architectural assertion.

Equally significant: **no architecture rule was edited, relaxed, or exempted.** The seven ArchUnit fitness functions and two Spring Modulith verifications passed unmodified throughout. The one case that could have forced an exemption — a `@RestController` in a technical module with no hexagon — was resolved by placing it at `platform.infrastructure.web` so the existing rule matched by construction (ADR-014, resolving the Step 2 review's forward-looking concern C-10).

**Satisfaction of original architectural goals:** achieved. The module dependency DAG gained zero edges; `platform` retains `allowedDependencies = {}`; the domain ring never learned that security exists; no database object was added; and one Boot-managed dependency was introduced in total.

**Readiness to proceed:** **GREEN**, following Phase 0. The step's Definition of Done is met on all ten criteria. One high-severity finding raised at closure review (development credentials shipping in the default configuration) has been remediated in Phase 0 and is documented in §7.

---

## 2. Objectives Achieved

| # | Objective (Step 3 plan §1) | Outcome |
|---|---|---|
| 1 | Token issuance via `POST /auth/token` with configuration-backed principals | Delivered — `AuthTokenController` + `TokenService`, BCrypt-verified, RS256-signed |
| 2 | Stateless token validation by an OAuth2 resource server | Delivered — `oauth2ResourceServer(jwt)` over the project's own `JwtDecoder` |
| 3 | Role model EDUCATOR / LEARNER / ADMIN with a read/write split | Delivered — closed `Role` enum, matrix asserted cell by cell |
| 4 | Correct failure semantics: 401 + `WWW-Authenticate: Bearer`, 403 on role failure | Delivered — distinct entry point and access-denied handler, one test per failure mode |
| 5 | Interactive usability through Swagger Authorize | Delivered — `bearerAuth` scheme, screenshot evidence captured |
| 6 | Zero-touch modularity evidence | **Delivered and verified** — empty diff under `competency/src/main` |

Out-of-scope cut-lines held without exception: no identity persistence, no refresh tokens, no revocation, no external IdP, no rate limiting, no TLS termination. Each remains recorded in ADR-013 as an evolution path rather than an omission.

---

## 3. Architecture Decisions

**ADR-013 — Token issuance and principal strategy (Accepted).** A single self-issued token endpoint with configuration-backed development principals; RS256 pinned; environment-injected PEM key material; `aud` and `jti` deliberately omitted with recorded rationale. Amendment A5's fail-fast key policy is the decision's sharpest edge: a generated keypair exists only under the dev profile, and missing, partial, unparseable, or mismatched key material aborts startup anywhere else. Spring Authorization Server, Keycloak, and opaque tokens were each rejected with reasons.

**ADR-014 — Platform web-adapter convention (Accepted).** Platform controllers live under `platform.infrastructure.web`, satisfying `restControllersOnlyInWebAdapters` by construction. The alternatives — exempting platform from the rule, or manufacturing an empty four-ring hexagon around one controller — were rejected as, respectively, the end of the rule's evidential value and a structure claiming a domain model that does not exist.

**ADR-015 — Centralised URL-pattern authorisation (Accepted).** All coarse authorisation is one ordered rule set in one filter chain, terminating in `anyRequest().authenticated()`. `@PreAuthorize` scattered across modules and per-module filter chains were both rejected. The ADR records honestly what this costs: URL patterns cannot express resource ownership, and rule order is semantically significant.

**ADR-016 — Identity propagation (Accepted, Phase 0).** The caller's identity travels inward as an explicit command field extracted by the controller, never read from `SecurityContextHolder` inside a use case. Recorded now, ahead of Step 4, because the closure review found the fitness functions would not have caught the wrong choice.

**ADR-010** is realised by the above and annotated in the ADR index. Its full text remains an index entry pending transcription from the design log (§7).

---

## 4. Implementation Summary

Delivered in six sequenced phases, each verified before the next began.

**Phase 1 — key handling.** `JwtProperties`, `JwtKeyMaterial`, `JwtKeyConfigurationException`, and real openssl PEM fixtures. `JwtKeyMaterial` is deliberately `Environment`-free: it takes an explicit `devFallbackPermitted` flag so the policy is unit-testable branch by branch, and pair consistency is verified by modulus comparison rather than assumed.

*This phase also produced the step's one process failure.* The spike was committed to `src/` at the repository root, outside the `egas/` Maven module, so it never compiled and its seven tests never ran — while the commit message asserted they did. It was caught at the Phase 2 review by noticing the suite count had not moved from 50, and corrected in `ae9c11e`. It is recorded here rather than quietly fixed because the failure mode is instructive: a green build proves only what it actually executed.

**Phase 2 — encoder and decoder.** `JwtConfiguration` translates active profiles into the A5 flag, builds a Nimbus `JwtEncoder` over the RSA JWK, and a `JwtDecoder` with RS256 pinned. Issuer validation was consciously deferred and recorded as an extension point on the bean, then implemented in Phase 5 — the deferral was tracked, not forgotten.

**Phase 3 — credentials and minting.** `PrincipalProperties` (strict bind-time validation: blank fields, plaintext-where-a-hash-belongs, empty roles, duplicate usernames all abort), the closed `Role` enum, `PrincipalConfiguration`, and `TokenService`. Anti-enumeration is structural rather than incidental: an unknown username is compared against a constant real BCrypt hash, so both failure paths cost the same and raise an identical exception.

**Phase 4 — web adapter.** `AuthTokenController`, `AuthExceptionHandler`, and the OAuth2-shaped DTOs. `TokenRequest` overrides `toString()` so a password cannot reach a log through a record's generated output. Issuance returns an internal `IssuedToken` rather than Spring's `Jwt`, keeping the resource server's vocabulary out of the issuing contract.

**Phase 5 — resource server migration.** The `SecurityConfig` rewrite, resolving a genuine bootstrap deadlock: `/auth/token` had been subject to `anyRequest().authenticated()`, so the endpoint issuing tokens demanded one. Both the `roles` claim name and the `ROLE_` prefix are set explicitly on a single converter, because the defaults (`scope`, `SCOPE_`) would produce zero matching authorities and manifest only as unexplained 403s.

**Phase 6 — documentation and evidence.** Bearer scheme, ADRs, `security-view.puml`, and the evidence pack.

**Phase 0 (post-closure hardening).** Development principals moved to `application-dev.yml` with a startup guard; `applicationStaysOutOfAdapters` strengthened to bar `org.springframework.security..`; ADR-016; this review.

---

## 5. Testing Evidence

92 tests, 0 failures, executed against real PostgreSQL — 87 at Step 3 closure plus five added by Phase 0 hardening. The plan projected 70–76; the surplus is coverage of properties that emerged as worth asserting, not padding.

| Suite | Tests | What it establishes |
|---|---|---|
| `JwtKeyMaterialTests` | 7 | Every A5 branch: configured pair, dev fallback, refusal outside dev, partial configuration, unparseable PEM, mismatched pair |
| `JwtKeyConfigurationTests` | 4 | The same policy at context level — a non-dev context without valid keys fails *startup*, not first use |
| `TokenServiceTests` | 9 | Claim correctness against a fixed `Clock`; `aud`/`jti` absent; tamper rejection; identical failure for unknown user and wrong password; BCrypt proven to run on both paths |
| `PrincipalConfigurationTests` | 5 | Development principals rejected outside dev, admitted under it, and the guard proven not to fire on legitimate rosters |
| `AuthTokenApiTests` | 5 | The HTTP contract, including byte-identical bodies for the two credential failures |
| `SecurityAuthorizationTests` | 10 | The role matrix and every failure mode, using **real minted tokens** rather than test post-processors |
| `OpenApiSecuritySchemeTests` | 2 | The published document advertises the scheme and exempts the token endpoint |
| `CompetencyFrameworkApiTests` | 8 | Unchanged assertions under real bearer semantics |
| Architecture (`HexagonalArchitectureTests`, `ModularityTests`) | 9 | Seven fitness functions plus two Modulith verifications, passing unmodified |

Two testing decisions are worth recording. `SecurityAuthorizationTests` uses tokens obtained from the live endpoint rather than `jwt()` post-processors, because a post-processor bypasses precisely the machinery under test — decoder, validators, converter. And the anti-enumeration property is asserted twice at different altitudes: as equal BCrypt invocation counts in the unit test (via a hand-rolled counting decorator, the codebase using no mocking framework) and as byte-identical HTTP bodies in the integration test.

---

## 6. Security Evidence

Four artefacts in `docs/evidence/security/`, none fabricated. The three transcripts are regenerated by `capture-evidence.sh` against a running instance rather than committed as prose, so they cannot drift from actual behaviour; the Swagger screenshot is a genuine manual capture.

Artefact 01 shows a real issuance exchange with the decoded header (`{"alg":"RS256"}`) and claims (`iss`, `sub`, `exp`, `iat`, `roles`; no `aud`, no `jti`). Artefact 03 shows 401 with `WWW-Authenticate: Bearer` for both an absent and a malformed token. Artefact 04 shows a LEARNER holding a valid token receiving 403 — with `error="insufficient_scope"` in the challenge, confirming the access-denied handler rather than the entry point produced it, which is exactly the 401/403 distinction the design turns on.

The private key is never read or printed by the capture script. Signatures do appear, which is safe: a signature is public data, unlike the key that produced it.

---

## 7. Limitations and Accepted Trade-offs

**Remediated in Phase 0 (raised at closure review).** Development principals were defined in the default `application.yml` and therefore active in every profile, shipping inside the production jar with their plaintext passwords documented in the same file — one of them holding ADMIN. A deployment correctly injecting JWT keys but forgetting to override the roster would have started successfully with three well-known accounts. This was a structural asymmetry: A5 made key misconfiguration abort loudly while credential misconfiguration failed silently. Now the roster lives in `application-dev.yml`, and a `dev-`prefixed principal outside the dev profile aborts startup.

**Accepted, recorded in ADR-013.** No refresh tokens (re-authenticate on expiry); no revocation or denylist (tokens live to `exp`, and reintroducing `jti` plus persistence is the documented path); no login rate limiting or account lockout — proportionate for a single-tenant academic prototype, though `/auth/token` is now genuinely public and this is the residual risk most worth revisiting if the prototype is ever exposed beyond a controlled setting. Identity persistence remains deliberately out of scope.

**Accepted, structural.** ADR-015's URL patterns cannot express resource ownership, which is why ADR-016 exists and why Step 4 splits authorisation across two layers. Rule ordering in the filter chain is semantically significant; the mitigation is the matrix test asserting every cell rather than reviewer vigilance.

**Open, low severity.** The packaged jar's fail-fast path is proven by `ApplicationContextRunner` at context level but has never been executed as `java -jar` — the mechanism is identical, so this is an inference rather than evidence, and a single manual run would close it. ADR-010 has no file; rows 001–010 of the index remain entries pending transcription from the design log, which the dissertation's security chapter will want. Integration tests share mutable database state, keeping uniqueness by naming convention with no per-test rollback; it works today and grows more fragile with each suite added. Development tokens do not survive a restart, since the dev keypair is regenerated — expected, and relevant only when comparing evidence captures across sessions.

---

## 8. Definition of Done — Step 3

| # | Criterion | Status |
|---|---|---|
| 1 | `mvn verify` BUILD SUCCESS, full suite green on real PostgreSQL | **Met** — 92 tests, 0 failures |
| 2 | Unauthenticated → 401 with `WWW-Authenticate: Bearer`; insufficient role → 403, both test-proven | **Met** |
| 3 | Role matrix holds: LEARNER read-only, EDUCATOR/ADMIN register | **Met** |
| 4 | `/auth/token` issues RS256 JWTs with verified signature, expiry, roles; expired/tampered → 401 | **Met** |
| 5 | Swagger Authorize completes a register-and-fetch cycle, documented with a screenshot | **Met** |
| 6 | Nine architecture tests pass unchanged; no modifications under `competency/src/main` | **Met** — verified by empty diff |
| 7 | ADR-013/014/015 committed as Accepted, ADR-010 annotated, index updated | **Met** |
| 8 | Security-view diagram committed | **Met** |
| 9 | `docs/evidence/security/` populated with four artefacts | **Met** |
| 10 | Step report delivered; stop-and-wait observed | **Met** — this document |

---

## 9. Next-Step Recommendations

**Step 4 should be Learner Profiling.** It is the only remaining blocker on the analytical core: `gapanalysis` declares `allowedDependencies = {"competency :: api", "learner :: api"}`, and `recommendation` sits downstream of that, so building anything else first leaves the dissertation's central contribution unreachable. Its schema and Flyway range (`learner`, V200–V299 per ADR-011) are already reserved, and its `api` stub exists.

Step 4 is also where ADR-015's deferred ownership question comes due, which is a further argument for taking it while the security design is fresh. ADR-016 settles the mechanism in advance, and the strengthened fitness function now enforces it.

Suggested sequence, mirroring the cadence that worked here: domain slice first (`LearnerProfile` aggregate, evidence value objects, proficiency resolution — framework-free, unit-tested); then persistence (`V200__`, JPA adapter behind a port, no cross-schema foreign keys); then application and contract (commands carrying the authenticated subject per ADR-016, `learner :: api` exposing identifiers only); then the web adapter with ownership authorisation, recorded as the ADR-015 amendment that ADR promised; then documentation and evidence.

Two smaller items should be scheduled independently of Step 4: transcribing ADR-001…010 from the design log before submission, and one manual `java -jar` run to convert the fail-fast inference into evidence.
