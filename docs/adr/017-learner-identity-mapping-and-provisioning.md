# ADR-017: Learner identity mapping and profile provisioning

Status: Accepted
Date: 2026-08-03

## Problem
ADR-016 settles *how* the caller's identity reaches a use case — as an explicit command field
carrying the JWT `sub` claim — but not what that string maps onto. Learner Profiling is the first
context that owns a resource belonging to a particular person, so it must answer two questions the
earlier steps could avoid. Which domain object does a subject correspond to, and how does that
correspondence come into existence?

`LearnerId`, written in Step 1 and unchanged since, constrains the answer in its own javadoc:
"Distinct from any authentication principal id on purpose: the security identity (platform
concern) and the domain identity (profiling concern) must be free to evolve independently; the
mapping between them is an application-layer concern." A `LearnerId` derived from the subject —
by hashing it into a UUID, say — would collapse the two identities the stub deliberately separates,
and would make a change of identity provider a change of every learner's primary key.

The `learner` module also cannot reference `platform`, where all security machinery lives
(`allowedDependencies = {"competency :: api"}`), so the mapping cannot be held by the security
layer on the module's behalf.

## Alternatives
1. Derive `LearnerId` deterministically from the subject (UUIDv5 over `sub`). No mapping to store
   and `/me` needs no lookup — but domain identity becomes a function of the authentication
   provider, contradicting the Step 1 contract, and re-keying every profile is the cost of ever
   changing that provider.
2. A dedicated mapping table (`subject → learner_id`) owned by the application layer, with the
   aggregate holding no subject at all. Purist about the separation, and the aggregate stays
   ignorant of authentication — but it introduces a second persistence structure, a second
   round-trip on the hot `/me` path, and an integrity rule (exactly one mapping per profile) that
   the database can only enforce across two tables.
3. The aggregate holds the subject as an attribute under a unique constraint, and the repository
   exposes `findByAuthSubject` (chosen).

## Decision
Option 3. `LearnerProfile` carries an `AuthSubject` value object alongside its independent
`LearnerId`, and `learner.profile.auth_subject` carries a unique constraint. The mapping
is resolved by `LearnerProfileRepository.findByAuthSubject`, so it remains an application-boundary
concern in the sense the Step 1 javadoc intends — the domain never asks who is authenticated, it
is told — while staying one table, one lookup, and one database-enforced invariant.

`AuthSubject` is deliberately an opaque, validated string rather than a parsed username. It is
non-blank, trimmed, and bounded at 200 characters, and nothing in the domain interprets its
content. ADR-013 anticipates a real identity source replacing configuration-backed development
principals; when that happens the string's content changes and no signature, column type, or
domain rule changes with it.

**Provisioning is an explicit act.** A profile is created by `POST /api/learners/me`, taking the
subject from the token and never from the request body. Auto-provisioning on first `GET /me` was
rejected: it performs a write inside a read, makes an ostensibly safe verb non-idempotent, and
obscures the moment a person entered the system — which for a resource holding personal data is
exactly the moment worth being able to point at.

The one-profile-per-subject invariant is enforced twice, deliberately: by a fast-path existence
check in the application service and by the unique constraint, whose violation the persistence
adapter translates back into `DuplicateLearnerProfileException`. The check is a courtesy; the
constraint is the authority. This is the check-then-act pattern already proven for framework
registration in Step 2.

*Correction (Step 4 Phase 3, 2026-08-03): this section originally named the table
`learner.learner_profile`, the name the Step 4 plan proposed before implementation. The table
delivered by `V200__create_learner_profile_tables.sql` is `learner.profile`, and the constraint is
`uq_learner_auth_subject`. The decision is unchanged; only the identifier is corrected.*

## Consequences
`/me` resolves in a single indexed lookup. A learner's domain identity survives any change of
authentication provider, since only the stored subject string would need rewriting. Because the
subject is an ordinary attribute, the ownership predicate is a plain equality comparison inside
the aggregate — no security infrastructure is required to test it, which is what makes the ADR-016
payoff demonstrable rather than merely asserted.

## Trade-offs
The aggregate knows a fact that originates in the security layer, which a strict reading of
context autonomy would place elsewhere; the mitigation is that it knows it as an opaque string
with no behaviour attached, so no security semantics cross the boundary. Explicit provisioning
means a learner holding a valid token may have no profile, and every read path must handle that
absence — accepted, and it is honest: authentication and enrolment are genuinely different events.
A subject that changes upstream (a renamed principal) orphans its profile until the stored value
is updated; no rename path exists in this step, and none is needed while principals are
configuration-backed.

## Quality attributes affected
Modifiability (+ identity provider substitutable without re-keying), testability (+ ownership
provable without a security context), integrity (+ database-enforced uniqueness), performance
(+ single-lookup `/me`), context autonomy (− the aggregate stores an authentication-derived value,
bounded to an opaque string).

## Future evolution
Should a profile ever need several credentials — institutional SSO alongside a local account — the
attribute becomes a collection and the unique constraint moves to a child table, without
disturbing `LearnerId` or any consumer. If GDPR erasure becomes in scope, the subject is the field
to clear first, since it is the only value linking a profile to a person outside the system.
