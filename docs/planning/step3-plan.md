# EGAS Step 3 Plan — Authentication & Authorisation (Realisation of ADR-010)

**Status:** PROPOSED (Rev 3) — awaiting final approval; no implementation authorised
**Date:** 3 August 2026 · **Baseline:** Step 2 (50 tests green, committed, pushed)
**Amendment log:** Rev 2 (3 Aug 2026) — **A1:** principal terminology revised to "configuration-backed development principals" throughout, with identity persistence explicitly recorded as intentionally outside Step 3 scope; **A2:** security evidence pack `docs/evidence/security/` added as a Definition-of-Done requirement (§8.1).
**Rev 3 (3 Aug 2026, per final architecture gate review)** — **A3:** architecture-test count corrected throughout to the precise wording "seven ArchUnit fitness functions plus the two Spring Modulith verification tests — nine architecture tests in total", with an erratum note covering the Step 2 review's historical count (§8); **A4:** ADR-015 (centralised URL-pattern authorisation) added (§2.2, §3, DoD row 7); **A5:** fail-fast key policy outside the dev profile, `aud`/`jti` omissions recorded in ADR-013, configuration-validation tests added and suite estimates adjusted (§2.4, §3, §6, §7, §8, §9).
**Governing constraint:** all Step 1/Step 2 architecture rules (seven ArchUnit fitness functions plus the two Spring Modulith verification tests — nine architecture tests in total — together with the locked `allowedDependencies` DAG and deny-by-default security) remain enforced **unchanged**. No rule is edited, relaxed, or exempted in this step.

---

## 1. Objectives

Step 3 turns the deny-by-default shell into a complete, standards-anchored security capability:

1. **Token issuance** — `POST /auth/token`: **configuration-backed development principals** (BCrypt-hashed credentials held in application configuration) exchange username/password for a self-issued **RS256 JWT** carrying a `roles` claim. Identity persistence — user accounts, registration, credential storage in the database — is **intentionally outside Step 3 scope**: principals are a development-time construct pending any future identity subsystem, and ADR-013's rationale records this as a deliberate scoping decision, not an omission.
2. **Token validation** — Spring Security OAuth2 Resource Server validates every business request against the issuing public key; stateless, per ADR-010.
3. **Role model** — `EDUCATOR`, `LEARNER`, `ADMIN`. Authorisation on the existing registry: `GET /api/frameworks/**` → any authenticated principal; mutating verbs → `EDUCATOR` or `ADMIN`.
4. **Correct failure semantics** — authentication failures → **401** with `WWW-Authenticate: Bearer` (RFC 6750); authorisation failures → **403**. Business errors remain RFC 9457 problem details — two standards, cleanly separated by concern.
5. **Interactive usability** — Swagger UI gains a bearer security scheme; "Authorize → Try it out" completes a full register-and-fetch cycle for the first time.
6. **Zero-touch modularity evidence** — no changes to any file under `competency/src/main` (test authentication mechanics only). This property is an acceptance criterion, not a hope.

**Out of scope (hard cut-lines, to be recorded in ADR-013):** identity persistence (user registration, accounts, credential storage — an intentional scoping decision stated explicitly in ADR-013's rationale); refresh tokens; token revocation or denylists; external identity providers (Keycloak/OIDC); login rate-limiting and account lockout; TLS termination. Each is documented as an evolution path, not silently omitted.

---

## 2. Architectural Changes

### 2.1 Platform module — new security capability (additive only)

```
platform/
├── security/                          (existing package, extended)
│   ├── SecurityConfig                 (rewritten: resource server, roles, bearer handlers)
│   ├── JwtProperties                  (@ConfigurationProperties: issuer, ttl, key material)
│   ├── PrincipalProperties            (configuration-backed development principals: username, bcrypt hash, roles)
│   ├── JwtConfiguration               (JwtEncoder/JwtDecoder beans — Nimbus; dev-profile
│   │                                   generated-keypair fallback when no keys injected)
│   └── TokenService                   (credential verification + JWT minting; injected Clock)
└── infrastructure/
    └── web/                           (NEW — per ADR-014)
        ├── AuthTokenController        (POST /auth/token)
        ├── dto/ (TokenRequest, TokenResponse)
        └── AuthExceptionHandler       (401 on bad credentials; uniform anti-enumeration body)
```

The `platform.infrastructure.web` placement is the resolution of concern **C-10**: the existing `restControllersOnlyInWebAdapters` rule (`..infrastructure.web..`) is satisfied **by construction**, unmodified. The platform module remains a technical module — the full four-ring hexagon is *not* mandated for it; only the web-adapter placement convention is binding (ADR-014).

### 2.2 SecurityConfig rewrite (single choke point)

- `oauth2ResourceServer(jwt)` with a `JwtAuthenticationConverter` mapping the `roles` claim to `ROLE_*` authorities (one converter — the only place the claim-to-authority contract lives).
- `BearerTokenAuthenticationEntryPoint` replaces the Step-1 `HttpStatusEntryPoint` (401 + `WWW-Authenticate: Bearer`, RFC 6750 error attributes); `BearerTokenAccessDeniedHandler` renders 403.
- Authorisation rules, in order: health/info and OpenAPI docs (existing permits, unchanged) → `POST /auth/token` permitAll (the only new permit) → `GET /api/frameworks/**` authenticated → mutating verbs on `/api/frameworks/**` `hasAnyRole(EDUCATOR, ADMIN)` → `anyRequest().authenticated()` (deny-by-default retained as the terminal rule).
- CSRF stays disabled; the rationale strengthens (pure bearer, no cookie state); the documented revisit trigger is unchanged.

**Authorisation-model ruling (ratified — recorded as ADR-015):** centralised URL-pattern rules in the single `SecurityFilterChain`, rather than `@PreAuthorize` scattered across controllers or per-module filter chains — one auditable security choke point, no security semantics leaking into domain/application modules, and deny-by-default reasoning preserved as a single ordered rule set. A hybrid is anticipated for Step 4, where learner-profile *ownership* checks require principal identity at the application level; ADR-015 records that as its designated extension point.

### 2.3 Token and claims design

RS256 only (algorithm pinned; decoder built from our own public key — no JWK fetching, no `alg` confusion surface). Claims: `iss` (configurable, default `egas`), `sub` = username, `iat`/`exp` (TTL configurable, default 3600 s), `roles` = string array. No OAuth scopes — roles are sufficient at this scale (recorded rationale in ADR-013). The encoder uses the injected `Clock` from Step 2's `TimeConfiguration` — placed there precisely for this — making expiry deterministic under test.

### 2.4 Configuration and dependencies

- `pom.xml`: **one** new dependency — `spring-boot-starter-oauth2-resource-server` (Boot-managed version; brings Nimbus JOSE). Nothing else.
- `application.yml`: `egas.security.jwt.*` (issuer, ttl, private/public key locations — environment-injected PEM) and `egas.security.principals` (configuration-backed development principals with BCrypt hashes of documented dev-only passwords; production must env-override). **Fail-fast key policy (A5):** the generated in-memory keypair fallback exists *only* under the dev profile and is loudly logged as dev-only; in any non-dev profile, missing or unparseable key material **aborts startup** through configuration validation — an instance outside dev must never silently self-sign.
- `OpenApiConfiguration`: bearer `SecurityScheme` plus global security requirement (Authorize button).

### 2.5 Build order within the step

Keys/properties spike first (highest unknown) → encoder/decoder beans → `TokenService` + unit tests → controller → `SecurityConfig` swap + converter → migrate existing API tests to `jwt(...)` post-processors → role-matrix and E2E tests → springdoc scheme → ADRs, diagrams, security evidence pack (§8.1), docs. The early migration of existing tests to `jwt(...)` decouples them from the issuance endpoint, so the 50-test baseline is protected throughout.

---

## 3. ADRs Required

| ADR | Title | Status on entry | Content |
|---|---|---|---|
| **ADR-013** (new) | Token issuance and principal strategy | Proposed → Accepted in-step | Minimal self-issued token endpoint; configuration-backed development principals (BCrypt-hashed); env-injected RSA PEM keys; RS256 pinned; 60-min TTL; claims shape. The rationale explicitly states that identity persistence (accounts, registration, credential storage) is intentionally outside Step 3 scope — a deliberate scoping decision pending any future identity subsystem, not an omission. **A5:** the fail-fast key policy is part of the decision — the generated keypair fallback is strictly dev-profile-only; in any non-dev profile, missing or invalid key material aborts startup via configuration validation (never silent self-signing). The ADR also explicitly records the deliberate omission of the `aud` and `jti` claims with rationale: `aud` adds nothing in a single-audience system (reintroduced if a second audience ever appears), and `jti` exists to support revocation and replay tracking, which are explicit non-goals of the stateless design (reintroduced with any future denylist). **Alternatives rejected:** Spring Authorization Server (full OAuth2 machinery disproportionate to a single-tenant prototype), external IdP such as Keycloak (operational weight orthogonal to the research questions), opaque tokens + introspection (reintroduces server-side state, defeating ADR-010's statelessness). Evolutions: refresh tokens, revocation denylist, external IdP federation, lockout/rate limiting. |
| **ADR-014** (new) | Platform web-adapter convention | Proposed → Accepted in-step | Platform controllers live under `platform.infrastructure.web`, satisfying the ADR-008 fitness function unmodified (resolves C-10); platform remains a technical module without mandated domain/application/api rings. |
| **ADR-015** (new) | Authorisation enforcement: centralised URL-pattern rules in the single SecurityFilterChain | Proposed → Accepted in-step | **Decision:** all coarse-grained authorisation is expressed as ordered URL-pattern rules in the one `SecurityFilterChain`. **Alternatives rejected:** (1) `@PreAuthorize` method-security annotations scattered across modules — authorisation fragments across the codebase, the single auditable choke point is lost, and security semantics leak into domain/application modules; (2) multiple module-specific security filter chains — deny-by-default reasoning fragments across chains and matcher-ordering pitfalls multiply. **Rationale:** preserves one auditable security choke point; avoids leaking security concerns into domain/application modules; maintains deny-by-default reasoning as a single ordered rule set terminating in `anyRequest().authenticated()`. **Recorded extension:** Step 4 may introduce a hybrid approach for resource-ownership checks (principal identity evaluated at the application level), to be captured as an ADR-015 extension when it lands. |
| ADR-010 | AuthN/Z: self-issued RSA JWTs | Accepted (unchanged) | Add a one-line "Realised in Step 3" note cross-referencing 013/014; index updated. |

Full ADR texts are authored at implementation start (rule 5), not in this plan.

---

## 4. Affected Modules

| Module | Production code | Tests |
|---|---|---|
| `platform` | **Additive:** security capability + `infrastructure.web` (≈ 9–11 new classes, 1 rewritten) | New unit + integration security tests |
| `competency` | **Zero changes** (acceptance criterion: empty `git diff` under `competency/src/main`) | `CompetencyFrameworkApiTests`: `user(...)` → `jwt(...)` with role authorities; assertions unchanged |
| root tests | — | `ApplicationSmokeTest`: 401 assertion gains `WWW-Authenticate: Bearer` check |
| `shared`, `learner`, `catalogue`, `gapanalysis`, `recommendation` | Untouched | Untouched |

Modulith DAG: unchanged — `platform` keeps `allowedDependencies = {}` (its new classes depend only on Spring/Nimbus/JDK). `HexagonalArchitectureTests` and `ModularityTests`: not edited, must pass as-is.

---

## 5. Database Changes

**None.** No Flyway migrations; version ranges untouched (history remains V1 + V100). This is deliberate, not omission: JWT validation is stateless — the scalability precondition ADR-010 promised — principals are configuration-backed development constructs — identity persistence is intentionally outside Step 3 scope, recorded as such in ADR-013 — and token revocation (the one feature that would otherwise demand persistence) is an explicit non-goal.

---

## 6. Security Considerations

1. **Deny-by-default preserved.** The terminal `anyRequest().authenticated()` survives; `POST /auth/token` is the *only* new permit, alongside the existing health/docs permits.
2. **401 vs 403 discipline.** Authentication failure (absent/malformed/expired/tampered token) → 401 with RFC 6750 bearer challenge; authorisation failure (valid token, insufficient role) → 403. Each path gets a dedicated test — conflating these is the classic resource-server defect.
3. **Anti-enumeration at issuance.** Unknown user and wrong password return byte-identical 401 responses; BCrypt verification runs in both paths.
4. **Key hygiene and fail-fast policy (A5).** Private key environment-injected, never logged, never committed; PEM parsing via Spring's `RsaKeyConverters`. The generated fallback keypair exists **only** under the dev profile — in-memory, regenerated per start, logged loudly as dev-only. In any non-dev profile, missing or invalid key material **fails startup** via configuration validation: an instance outside dev must never silently self-sign, because a silently self-signing instance would mask precisely the misconfiguration that matters most.
5. **Algorithm pinning.** RS256 only; decoder constructed from our public key — no remote JWKS, no `none`, no HS/RS confusion surface.
6. **Dev credentials.** BCrypt hashes of clearly named dev-only passwords in `application.yml`; production env-override documented; no real secrets in the repository, ever.
7. **Accepted residual risks (recorded):** no login rate-limiting/lockout and no token revocation — proportionate for a single-tenant academic prototype, listed as production hardening in ADR-013. TLS termination remains a deployment concern outside scope.
8. **Documentation exposure** unchanged from Step 2 (recorded decision); the published OpenAPI spec contains no secrets.

---

## 7. Testing Strategy

| Tier | New/changed classes | ~Tests | What it proves |
|---|---|---|---|
| Unit | `TokenServiceTests`, `JwtKeyConfigurationTests` (A5) | 8–11 | Claims correctness (`iss`, `sub`, `roles`, `iat`/`exp` from fixed Clock); signature verifies against the paired public key; tampered payload rejected; wrong password and unknown user rejected identically. **Configuration validation (A5):** a non-dev profile with missing key material fails context startup; a non-dev profile with unparseable PEM fails context startup; the dev profile without keys activates (and loudly logs) the generated fallback |
| Integration — issuance | `AuthTokenApiTests` | 4–5 | 200 with parseable RS256 JWT of the expected shape; 401 wrong password; 401 unknown user (identical body); endpoint reachable unauthenticated |
| Integration — authorisation | `SecurityAuthorizationTests` | 8–10 | The role matrix: no token → 401 + `WWW-Authenticate: Bearer`; malformed token → 401 `invalid_token`; expired token → 401; LEARNER `GET` 200 / `POST` 403; EDUCATOR `POST` 201; ADMIN `POST` 201; **one true end-to-end**: obtain token from `/auth/token`, then register and fetch a framework with it |
| Migration | `CompetencyFrameworkApiTests`, `ApplicationSmokeTest` | (existing) | Same behavioural assertions under real bearer semantics; smoke test asserts the bearer challenge header |
| Architecture | — unchanged — | 9 | Seven ArchUnit fitness functions plus the two Spring Modulith verification tests — nine architecture tests in total — pass **without modification**: the step's structural gate |

Fixtures: a fixed test RSA keypair via `@TestConfiguration`; fixed `Clock` for expiry cases (mint tokens expired by minutes, comfortably beyond the decoder's default skew). Expected suite total: **~70–76 tests, 0 failures**, all on real PostgreSQL as before.

---

## 8. Definition of Done — Step 3

| # | Criterion |
|---|---|
| 1 | `mvn verify` → BUILD SUCCESS; full suite green (≈ 70–76 tests, 0 failures/errors) on real PostgreSQL |
| 2 | Unauthenticated business request → 401 with `WWW-Authenticate: Bearer`; insufficient role → 403 (both test-proven) |
| 3 | Role matrix holds: LEARNER read-only; EDUCATOR/ADMIN can register (201) |
| 4 | `/auth/token` issues RS256 JWTs whose signature, expiry, and `roles` claim are verified by automated tests; expired/tampered → 401 |
| 5 | Swagger Authorize → Try-it-out completes a manual register-and-fetch cycle (documented with screenshot for the dissertation) |
| 6 | Seven ArchUnit fitness functions plus the two Spring Modulith verification tests — nine architecture tests in total — pass unchanged; `git diff` shows **no modifications under `competency/src/main`** |
| 7 | ADR-013, ADR-014, and ADR-015 committed (Accepted), ADR-010 annotated, index updated |
| 8 | Security-view diagram added (`docs/diagrams/security-view.puml`: token flow + filter chain) |
| 9 | `docs/evidence/security/` populated and committed (A2): JWT issuance-flow evidence, Swagger bearer-authorisation screenshot, 401 authentication-failure evidence with the `WWW-Authenticate: Bearer` header visible, 403 authorisation-failure evidence |
| 10 | Seven-part step report delivered; stop-and-wait observed (rule 11) |

*Note (A3): the committed Step 2 completion review carries a historical counting inconsistency — its §9.6 refers to "nine ArchUnit rules" while its §5 correctly counts seven. Per governance practice the committed historical document is not modified; the Step 3 step report will record the erratum.*

### 8.1 Security evidence pack — `docs/evidence/security/` (amendment A2)

Step 3 does not close until the following four artefacts exist under `docs/evidence/security/` and are committed with the step:

| # | Artefact | Suggested file | Capture method |
|---|---|---|---|
| 1 | JWT issuance flow | `01-jwt-issuance-flow.md` | Scripted `curl` transcript of `POST /auth/token` with a development principal: request, full response, and the base64url-decoded JWT header + claims. The signature may appear (it is not secret); the private key is never captured anywhere. |
| 2 | Swagger bearer authorisation | `02-swagger-bearer-authorization.png` | Manual screenshot: the Authorize dialog with the bearer token applied, plus one authorised try-it-out returning 201/200. |
| 3 | 401 authentication failure | `03-401-authentication-failure.md` | Scripted `curl -i` with no token against a business endpoint: status line `401` and the `WWW-Authenticate: Bearer` response header visible in the transcript. |
| 4 | 403 authorisation failure | `04-403-authorization-failure.md` | Scripted `curl -i` using a LEARNER token attempting `POST /api/frameworks`: status line `403`. |

**Reproducibility:** a small capture script (`docs/evidence/security/capture-evidence.sh`) is delivered with the step so the three transcript artefacts regenerate on demand against a running development instance; only the Swagger screenshot is manual. These artefacts double as dissertation figures for the security section and extend the evidence list of the Step 2 completion review (§6).

---

## 9. Risks and Mitigations

| Risk | Likelihood/Impact | Mitigation |
|---|---|---|
| PEM/key parsing friction (formats, headers, line endings) | Med / Med | First task is a key-handling spike; `RsaKeyConverters` + fixed test keypair; dev generated fallback isolates local runs from key provisioning; fail-fast configuration validation (A5) turns non-dev misconfiguration into a loud startup failure rather than a latent defect |
| `roles` claim ↔ `hasRole` prefix mismatch (silent 403s) | Med / High | Single `JwtAuthenticationConverter` as the only mapping site; role-matrix tests assert every cell |
| 401/403 conflation | Med / High | Distinct entry point vs access-denied handler; dedicated tests per failure mode |
| Expiry-test flakiness (clock skew) | Med / Low | Injected `Clock` at the encoder; expired fixtures minted minutes past, beyond default skew |
| Existing 8 API tests break mid-step | Med / Med | Migrate them to `jwt(...)` post-processors **first** — independent of the issuance endpoint, protecting the baseline throughout |
| springdoc bearer-scheme quirks | Low / Low | Manual verification is DoD #5; curl fallback documented if the UI misbehaves |
| Dev credentials treated as real | Low / Med | Names like `dev-educator`; loud dev-only logging; env-override documented; ADR-013 records the boundary |
| Scope creep toward user management | Med / Med | Cut-lines in §1 are contractual; ADR-013 lists them as rejected-for-now with evolution paths |
| Dependency risk | Low | Single Boot-managed starter; no version pin to drift |

---

*Rev 3 — amendments A1–A5 incorporated (A3–A5 per the final architecture gate review). Awaiting final approval. On your go-ahead, implementation begins with the key-handling spike and proceeds in the §2.5 order, with rulings restated before each code section per rule 4. Recommended repository location once approved: `docs/planning/step3-plan.md`.*
