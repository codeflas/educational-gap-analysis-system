# ADR-015: Authorisation enforcement — centralised URL-pattern rules in one SecurityFilterChain

Status: Accepted
Date: 2026-08-03

## Problem
ADR-010 settles authentication (self-issued RSA JWTs, stateless resource server) but not where
authorisation is *expressed*. Step 3 introduces the first role-differentiated rules: reading the
framework registry is open to any authenticated principal, changing it requires EDUCATOR or ADMIN.
Those rules can live in the security configuration, on the methods they protect, or in per-module
filter chains — and the choice determines whether the system's access policy can be read in one
place or must be reconstructed by searching the codebase.

## Alternatives
1. `@PreAuthorize` method security on controllers or services — authorisation fragments across
   every module; there is no single place that answers "what is protected and how"; and security
   semantics leak into application and domain code, which the hexagonal rules exist to keep clean.
2. One `SecurityFilterChain` per module — deny-by-default reasoning fragments across chains, and
   correctness starts depending on chain ordering and matcher precedence, a notoriously silent
   failure mode.
3. Centralised, ordered URL-pattern rules in the single chain (chosen).

## Decision
Option 3. All coarse-grained authorisation is expressed as ordered `requestMatchers` in the one
`SecurityFilterChain` in `SecurityConfig`, terminating in `anyRequest().authenticated()` so
deny-by-default remains the rule that catches everything not named above it. The ordered set is:
health/info probes and API documentation permitted; `POST /auth/token` permitted — the only new
permit of Step 3, without which the endpoint issuing tokens would demand one; `GET
/api/frameworks/**` authenticated; remaining verbs on `/api/frameworks/**` restricted to EDUCATOR
or ADMIN; everything else authenticated.

The claim-to-authority contract lives in exactly one place with it: a single
`JwtAuthenticationConverter` reading the `roles` claim and applying the `ROLE_` prefix. Both the
claim name and the prefix are set explicitly rather than left to defaults — the default converter
reads `scope` and prefixes `SCOPE_`, and a mismatch between the two halves manifests only as
unexplained 403s. `hasAnyRole` is fed from the `Role` enum's constants, so the enum and the rules
cannot drift apart.

Authentication and authorisation failures are rendered by distinct handlers —
`BearerTokenAuthenticationEntryPoint` (401 with the RFC 6750 `WWW-Authenticate: Bearer`
challenge) and `BearerTokenAccessDeniedHandler` (403) — because conflating them is the classic
resource-server defect. Each failure mode has its own test in `SecurityAuthorizationTests`,
exercised with real minted tokens rather than test post-processors, so the decoder, the issuer and
timestamp validators, and the converter are all genuinely on the path.

## Consequences
The complete access policy is readable in one ordered list and reviewable as a unit. Domain and
application code contains no security annotations whatsoever, so the hexagonal fitness functions
stay meaningful. The Step 3 capability was delivered with zero changes under `competency/src/main`
— the zero-touch modularity criterion — because authorisation never had to reach into the module
it protects.

## Trade-offs
URL patterns are coarse: they cannot express "this learner may read *their own* profile", since
that predicate needs the resource, not just the path. Rule order becomes semantically significant,
so an insertion in the wrong position can silently widen access — mitigated by the role-matrix
tests asserting every cell rather than by reviewer vigilance. Rules are also decoupled from the
handlers they govern, so a renamed path must be changed in two places.

## Quality attributes affected
Auditability (+ one choke point), conceptual integrity (+ no security in domain/application),
modifiability (+ policy changes are local; − path renames touch two files), correctness risk
(− order sensitivity, covered by tests), expressiveness (− no resource-level predicates).

## Future evolution
Step 4's learner-profile ownership checks need principal identity evaluated against the resource,
which URL patterns cannot express. The designated extension point is a hybrid: coarse rules stay
here, and ownership predicates are evaluated at the application level where the resource is in
hand. That extension is to be recorded as an amendment to this ADR when it lands, keeping the
rationale for the split in one place.
