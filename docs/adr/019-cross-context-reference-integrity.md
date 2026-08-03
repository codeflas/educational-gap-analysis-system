# ADR-019: Cross-context reference integrity for competency identifiers

Status: Accepted
Date: 2026-08-03

## Problem
A `ProficiencyAssertion` names a `CompetencyId` and a `CompetencyFrameworkId` that belong to
another bounded context. Nothing in the system currently checks that those identifiers refer to
anything real. `competency.api` publishes exactly two records — `CompetencyId` and
`CompetencyFrameworkId` — and no query contract: there is no published way to ask whether a
competency exists, which framework contains it, or what levels that framework defines. ADR-011
forbids a cross-schema foreign key, so the database will not catch it either. A learner could
therefore hold evidence against a competency identifier that has never existed.

Step 4 must either accept that, or create the missing contract — and creating it means modifying
the frozen Competency Modelling module, which has not been touched since Step 2 and whose
untouched state is itself the zero-touch modularity evidence the dissertation cites.

## Alternatives
1. Add a published query port to `competency.api` (`boolean exists(CompetencyId)` or similar),
   called synchronously by the learner application service. Gives immediate referential validity —
   at the cost of modifying the frozen module, introducing a synchronous runtime dependency
   between two contexts that currently share only value types, and creating exactly the coupling
   that a future extraction of either context would have to unpick. It would also be a lie about
   timing: a competency valid at write time can be removed afterwards, so the check buys
   point-in-time validity, not integrity.
2. Replicate a competency catalogue inside the learner schema, maintained by integration events,
   and validate against the replica. Sound, and precisely what ADR-007 already plans for Gap
   Analysis — but no such event exists yet (the `ModelPublished` event and its projection are
   scheduled for W6), so Step 4 would have to build the eventing infrastructure as a prerequisite,
   inverting the delivery order for a benefit only Step 5 consumes.
3. Accept the reference as an unvalidated value, and place validation where the projection will
   already exist (chosen).

## Decision
Option 3. `CompetencyId` and `CompetencyFrameworkId` are stored as opaque, well-formed UUID values
with no existence check at write time. The learner context treats them exactly as the V1 migration
header prescribes for all cross-context references: identifier values, no foreign keys, and
consistency as "an application/event concern by design".

Validation belongs to Gap Analysis, which under ADR-007 will hold a compiled read projection of
published competency models rebuilt from integration events. Once that projection exists, an
assertion naming an unknown competency is detectable there — where the data already is, without a
synchronous call and without either context depending on the other at runtime. Until then, an
unresolvable reference degrades to a competency that contributes nothing to a gap computation,
which is the correct behaviour for a reference to something that is not in the target model
anyway.

This decision is recorded rather than left implicit because it is a genuine weakening, and a
reader is entitled to know it was chosen rather than overlooked. What the system does guarantee at
write time is internal: identifiers are well-formed UUIDs, an assertion's framework is consistent
across all of its evidence, and at most one assertion exists per learner per competency.

## Consequences
Competency Modelling remains untouched, so the empty diff under `competency/src/main` continues to
hold across a second step — the zero-touch property becomes a trend rather than an anecdote. The
two contexts share value types only, with no runtime call between them, which keeps the extraction
story intact and the coupling measurements for RQ2 unpolluted. The learner module's
`allowedDependencies` stays `{"competency :: api"}` and the module DAG gains no edge.

## Trade-offs
A profile can hold assertions against identifiers that never existed, and nothing in Step 4 will
say so; the failure surfaces later as a competency absent from analysis rather than as a rejected
request, which is a worse error message at a later time. A typo in a client's request is
indistinguishable from a legitimate reference to a framework this instance has not yet imported.
Both are accepted for the slice, and both are closed by the same future projection.

## Quality attributes affected
Modularity (+ no runtime coupling between contexts; + zero-touch preserved), extractability
(+ neither context calls the other), data integrity (− no referential validity for cross-context
references), diagnosability (− invalid references surface late and indirectly).

## Amendment 1 — competency identity is derived from (frameworkId, code) (Step 5, Accepted 2026-08-03)

This ADR accepted that a `CompetencyId` held by another context is unvalidated. Step 5 planning
found something sharper, which this ADR did not anticipate: **nothing ever minted one**.

`CompetencyId` has been published in `competency.api` since Step 1 and is used nowhere inside the
Competency Modelling module. The M2 metamodel identifies a competency by `code`, framework-wide
unique, and declares no identifier attribute — so a UUID stored in a learner assertion corresponded
to nothing any model could produce. The references were not merely unvalidated; they were
*unmatchable*, and Gap Analysis could not have joined attainment to a model at all.

**Decision.** A competency's identity is **derived deterministically from its framework and its
code**: `CompetencyId = UUIDv3(frameworkId + ":" + competencyCode)`, computed via
`UUID.nameUUIDFromBytes` over UTF-8 bytes. Competency Modelling computes it when compiling a model
snapshot and exposes it in the framework detail response, so a client recording learner evidence
obtains a real identifier instead of inventing one.

Three properties make derivation the right answer here. It needs **no M2 change**, leaving the
ADR-003 metamodel freeze intact. It is **stable**: the same framework and code always yield the same
id, so re-registering or re-projecting a model does not re-key anything referring to it. And it is
**computable on both sides**, which is what a join key across a context boundary has to be when
neither side may read the other's tables. The technique is the same one Step 4 used for evidence row
ids, and for the same reason: identity derived from position in a stable structure, where no natural
key exists to store.

Codes are unique framework-wide by the metamodel's own well-formedness rules, so the derivation
cannot collide within a framework; including the framework id keeps two frameworks that reuse a code
apart.

**What this does not change.** References remain unvalidated at write time, exactly as decided above:
a learner may still hold an id for a competency that no registered model contains, and Gap Analysis
still reports that as absence rather than rejecting it. The amendment makes references *matchable*,
not *verified* — the projection-based validation described below remains the mechanism for the
latter.

## Future evolution
When the Gap Analysis projection lands (W6, ADR-007), reference validation becomes a report over
existing data — assertions whose competency is absent from the published model — and can be
surfaced as a profile health check without any change to the learner write path. Should validation
at write time ever become necessary, the projection can be queried through a published Gap
Analysis contract, which keeps the dependency pointing along the existing DAG edge rather than
creating a new one back into Competency Modelling.
