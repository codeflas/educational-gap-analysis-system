# ADR-013: Token issuance and principal strategy

Status: Accepted
Date: 2026-08-03

## Problem
ADR-010 fixes the security architecture (self-issued RSA JWTs validated by a Spring OAuth2
resource server) but leaves open how tokens are issued, where principals come from, how key
material is provisioned, and which claims a token carries. Each of these admits heavyweight
answers that would be disproportionate for a single-tenant academic prototype.

## Alternatives
1. Spring Authorization Server — full OAuth2 machinery (clients, grants, consent) for a system
   with one first-party client; disproportionate.
2. External identity provider (e.g. Keycloak) — operational weight orthogonal to the research
   questions; the dissertation's interest is the resource-server architecture, not IdP operation.
3. Opaque tokens with introspection — reintroduces server-side token state, defeating the
   statelessness ADR-010 promises as its scalability precondition.
4. Minimal self-issued token endpoint with configuration-backed development principals (chosen).

## Decision
A single `POST /auth/token` endpoint exchanges username/password for a self-issued JWT.

**Principals** are configuration-backed development principals: usernames, BCrypt password
hashes, and role lists held in application configuration. **Identity persistence — user
accounts, registration, credential storage in the database — is intentionally outside Step 3
scope.** This is a deliberate scoping decision pending any future identity subsystem, not an
omission: principals are a development-time construct, and the token contract is designed so a
real identity source can replace them without changing consumers.

**Signing** is RS256 only, pinned: the decoder is built from this instance's own public key —
no remote JWKS, no algorithm negotiation, no `none`. Token TTL defaults to 60 minutes
(configurable); expiry means re-authentication (no refresh tokens).

**Key material** is environment-injected PEM (PKCS#8 private, X.509 SubjectPublicKeyInfo
public). **Fail-fast policy (amendment A5):** a generated in-memory RSA-2048 fallback exists
only under the dev profile, loudly logged; in any non-dev profile, missing, partial,
unparseable, or mismatched key material aborts startup via configuration validation. An
instance outside dev never silently self-signs, because a silently self-signing instance would
mask precisely the misconfiguration that matters most. Enforced by `JwtKeyMaterial`
(pair-consistency checked by modulus comparison) and verified branch-by-branch in
`JwtKeyMaterialTests`; formats and signing round-trip were proven first by the Step 3
key-handling spike against real openssl output.

**Claims** are `iss`, `sub`, `iat`, `exp`, and `roles` (string array). Two deliberate
omissions, recorded with rationale: `aud` adds nothing in a single-audience system and is
reintroduced if a second audience ever appears; `jti` exists to support revocation and replay
tracking, which are explicit non-goals of the stateless design, and is reintroduced with any
future denylist.

## Consequences
Login yields a bearer token usable against every business endpoint; validation is stateless
and horizontally scalable; the whole capability adds one Boot-managed dependency and no
database objects. Misconfiguration is loud and early rather than latent.

## Trade-offs
No refresh tokens (re-authenticate on expiry), no revocation (tokens live to `exp`), no
account lockout or login rate-limiting — proportionate accepted risks for a single-tenant
academic prototype, each listed below as production hardening. Development credentials in
configuration are clearly named, dev-only, and environment-overridable; real secrets never
enter the repository.

## Quality attributes affected
Security (+ fail-fast, pinned algorithm, deny-by-default preserved), scalability (+ stateless),
operability (+ actionable startup failures), simplicity (+ one endpoint, zero persistence),
account safety (- managed: no lockout/revocation, recorded).

## Future evolution
Refresh tokens; revocation denylist (reintroduces `jti` and persistence); federation to an
external IdP replacing the issuance endpoint while the resource-server side stays unchanged;
login rate-limiting and lockout; per-environment key rotation procedures.
