# ADR-014: Platform web-adapter convention

Status: Accepted
Date: 2026-08-03

## Problem
Step 3 introduces the first controller outside a business module: `POST /auth/token` belongs to
the platform, which is a technical module with no domain, no aggregate and no persistence. The
ADR-008 fitness function `restControllersOnlyInWebAdapters` requires every `@RestController` to
reside in `..infrastructure.web..`. Platform has no such package, and it has no business owning
the full four-ring hexagon that business modules carry — there is nothing for a domain or
application ring to hold. Concern C-10 from the Step 3 gate review: does the token endpoint
force either an architecture-rule exemption or a ceremonial hexagon around one controller?

## Alternatives
1. Exempt platform from the fitness function — the rule acquires its first "except…" clause, and
   every later exception argues from this precedent. A rule with exceptions stops being evidence.
2. Give platform the full hexagon (`domain`, `application`, `api`, `infrastructure`) — empty rings
   created solely to satisfy a pattern; the structure would claim a domain model that does not
   exist and misleads every later reader.
3. Place platform controllers in `platform.infrastructure.web`, adopting the adapter-placement
   convention alone without the rest of the hexagon (chosen).

## Decision
Option 3. Platform controllers live under `ie.ul.egas.platform.infrastructure.web`, with DTOs in
its `dto` sub-package and error mapping alongside, matching the competency module's layout.
The fitness function passes **unmodified** — the pattern `..infrastructure.web..` matches by
construction, not by exemption. `AuthTokenController`, `AuthExceptionHandler`, `TokenRequest` and
`TokenResponse` are placed accordingly.

Platform remains a technical module: no domain ring, no application ring, no `api` named
interface, and `allowedDependencies = {}` unchanged, so no module may depend on it. Only the
web-adapter placement convention is binding; the rest of the hexagon is explicitly not mandated.

Adapter placement also fixes visibility: `TokenService` widens to public because package-private
access does not reach from `platform.security` into `platform.infrastructure.web`. Nothing
escapes the module regardless — the Modulith verification test forbids any cross-module
dependency on platform.

## Consequences
The security capability lands without editing a single architecture rule, which is what makes the
nine architecture tests usable as evidence rather than as documentation of the current code. The
convention generalises: any future technical-module endpoint has an obvious, rule-satisfying home.

## Trade-offs
Platform's internal structure is now deliberately asymmetric — one ring of the hexagon and not the
others — which reads as inconsistent until the rationale is known, hence this record. Widening
`TokenService` to public is a real (if module-contained) loss of encapsulation, accepted because
the alternative is a controller in the wrong package.

## Quality attributes affected
Architectural integrity (+ no rule exemptions), conceptual clarity (+ no empty rings; − asymmetric
module shape), encapsulation (− one type widened, contained by Modulith), modifiability (+ a
stated convention for later technical endpoints).

## Future evolution
Further platform endpoints (key rotation, operational actions) follow the same placement. Should
platform ever acquire genuine domain logic, the remaining rings are added then — driven by real
content rather than by symmetry.
