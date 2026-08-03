# ADR-016: Identity propagation — the caller's identity is command data, not ambient state

Status: Accepted
Date: 2026-08-03

## Problem
ADR-015 confines authorisation to ordered URL-pattern rules in one filter chain and records that
resource *ownership* checks — "this learner may read their own profile" — cannot be expressed that
way, because the predicate needs the resource, not just the path. Step 4 (Learner Profiling) is
the first slice to need one, so the mechanism by which an application service learns *who is
calling* must be settled before that code exists rather than discovered while writing it.

Three constraints bound the answer. The `learner` module declares
`allowedDependencies = {"competency :: api"}`, so it cannot reference any type in `platform`,
where all security machinery lives. The `domainIsFrameworkFree` rule bars `org.springframework..`
from every domain ring. And `applicationStaysOutOfAdapters` — as written before this ADR — barred
`..infrastructure..`, `org.springframework.web..`, `jakarta.servlet..` and `jakarta.persistence..`
from application services, but **not** `org.springframework.security..`.

That last gap is the immediate hazard. An application service calling
`SecurityContextHolder.getContext().getAuthentication()` would have passed all nine architecture
tests while introducing exactly the hidden coupling the rules exist to prevent: a use case whose
behaviour depends on thread-local state set by a servlet filter, untestable without a security
context, and silently wrong whenever invoked off the request thread — which the virtual-thread
posture of ADR-004 and any future asynchronous or event-driven path make a real prospect, not a
theoretical one.

## Alternatives
1. `SecurityContextHolder` read inside application services — zero plumbing, and the reason it is
   tempting; but the dependency is invisible in the signature, tests must install a security
   context to exercise business logic, and correctness silently depends on the calling thread.
2. A platform-owned `CurrentUser` port injected into application services — explicit in the
   constructor, but `learner` cannot depend on `platform` under the module DAG, so each module
   would need its own duplicate abstraction, and the ambient-state problem is relocated rather
   than removed.
3. The web adapter extracts the authenticated subject and passes it as an explicit field on the
   command object (chosen).

## Decision
Option 3. Identity enters the system exactly where the request does and travels inward as data.

The controller obtains the subject from `@AuthenticationPrincipal Jwt jwt` and places
`jwt.getSubject()` into the command it builds. Application services receive it as an ordinary
constructor or command parameter and evaluate ownership against the loaded resource. Domain types
receive a plain identifier — never a Spring type, never a token.

`applicationStaysOutOfAdapters` is amended to include `org.springframework.security..`, so the
decision is enforced by a fitness function rather than remembered. The rule count is unchanged at
seven ArchUnit rules plus two Spring Modulith verifications; this is a strengthening of an
existing rule, not a new one.

The subject carried is the JWT `sub` claim — the username of a configuration-backed development
principal today (ADR-013). Because it travels as an opaque string in a named command field, the
substitution ADR-013 anticipates — a real identity source supplying stable user identifiers —
changes what the string contains without changing any signature that carries it.

## Consequences
Every use case that depends on the caller says so in its own signature, so the dependency is
visible at the call site and in tests. Application and domain tests construct a command with a
subject string and need no security infrastructure whatsoever, which keeps the domain ring
genuinely framework-free rather than framework-free by inspection. Because nothing reads
thread-local state, use cases remain correct when invoked off the request thread — from an event
listener, a scheduled job, or a virtual thread that outlives the request.

## Trade-offs
Every controller touching an ownership-sensitive use case must thread the subject through
explicitly, which is more typing than one static call and easy to forget — mitigated because a
missing field is a compile error, whereas a forgotten `SecurityContextHolder` read is a runtime
authorisation hole. Commands grow a field that is not, strictly, user input, so command
construction must never take the subject from the request body; the controller is the only
legitimate source. Coarse authorisation therefore lives in the filter chain (ADR-015) and
ownership authorisation in the application layer, meaning a reader must consult two places to see
the whole policy — accepted, because the alternative is a filter chain that cannot express
ownership at all.

## Quality attributes affected
Testability (+ business logic testable without a security context), correctness (+ no
thread-affinity assumptions; + missing identity fails at compile time), conceptual integrity
(+ dependencies visible in signatures), modifiability (+ identity source substitutable without
signature churn), verbosity (− explicit threading at every call site), policy locality
(− authorisation split across two layers, by necessity).

## Future evolution
Should ownership checks proliferate, a domain-level `Owned` abstraction with a shared assertion
helper is the natural consolidation, still fed by the explicit subject. If a real identity
subsystem replaces ADR-013's principals, the `sub` claim's content changes and nothing else does.
Should the eventual answer need more than a subject — tenant, delegation chain, or impersonation
context — the command field becomes a small immutable value object carrying them, and the
enforcement rule stands unchanged.
