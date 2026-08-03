# ADR-018: Evidence-backed proficiency and a substitutable level-resolution policy

Status: Accepted
Date: 2026-08-03

## Problem
The Learner Profiling module's own charter, fixed in its `package-info` since Step 1, is "evidence
ingestion and resolution of proficiency levels against competencies". Two words there are load
bearing. *Evidence* implies that a learner's proficiency is inferred from observations rather than
declared as fact, and *resolution* implies a rule that turns several observations into one
answer. A profile storing a bare level per competency would satisfy Gap Analysis's immediate need
while contradicting the context's stated purpose, and would place a judgement — "this learner is
at level 3" — in the system with no record of why.

The question is therefore not whether to model evidence, but how much of an assessment subsystem
Step 4 is obliged to build, and where the rule that collapses evidence into a level should live.

## Alternatives
1. Store a self-declared level per competency, no evidence. Smallest slice, and Gap Analysis would
   not notice the difference — but the module's charter goes unmet, the provenance of every gap
   computed downstream is unrecoverable, and adding evidence later is a migration of every
   existing row rather than an extension.
2. Model evidence and hard-code the resolution rule as a method on the aggregate. Honest to the
   charter and simple — but the one genuine business *policy* in the context becomes the one thing
   that cannot be varied, and the aggregate acquires a reason to change that has nothing to do
   with its invariants.
3. A full assessment subsystem: assessment definitions, scoring schemes, moderation, evidence
   review workflow. Faithful to how competency attainment works in practice, and far outside a
   single vertical slice; it would consume the weeks the dissertation's core contribution needs.
4. Model evidence as immutable records inside the assertion, and express resolution as a
   substitutable domain port with one default implementation (chosen).

## Decision
Option 4. `EvidenceRecord` is an immutable value object — type, claimed level, confidence, source,
timestamp — appended to a `ProficiencyAssertion` and never modified. Resolution is
`LevelResolutionPolicy`, a single-method domain port taking the evidence set for one competency
and returning the `AttainedLevel`. The default `HighestConfidenceResolutionPolicy` selects the
most confident claim, breaking ties by recency, then by the higher ordinal, and finally by level
code, so the outcome is deterministic for any input.

The last tie-break is what makes that guarantee true rather than approximate, and is recorded
because it was initially missing. `AttainedLevel` orders by ordinal — the only comparison carrying
proficiency meaning — but a record's `equals` also compares the framework-scoped code, so ordering
by ordinal alone left `(2, "L2")` and `(2, "SFIA-2")` comparing as tied while being unequal. That
breaks the `Comparable` contract's consistency recommendation, and it broke this policy concretely:
evidence differing only in level code resolved according to the order it happened to be stored in.
Ordering by ordinal then code closes both at once. Records that tie on all four criteria
necessarily carry the same claimed level, so the resolved value is stable even though which record
"wins" is unspecified — the answer is deterministic, not the selection.

Evidence types are a closed enum — `SELF_DECLARED`, `ASSESSMENT`, `COURSE_COMPLETION`,
`CERTIFICATION`, `OBSERVATION` — because an open string would let the resolution policy's input
space grow without the policy being reconsidered.

Resolution is deliberately a **port rather than a method**, for three reasons. It is the only real
policy in a context that is otherwise bookkeeping, so it is the only thing whose variation is
interesting. It is the natural test seam: a lambda policy lets every aggregate invariant be
exercised without reasoning about resolution arithmetic, exactly as `ConformanceValidator` did for
`CompetencyFramework` in Step 2. And it gives the dissertation a second, independent instance of
the substitutable-strategy technique that ADR-006 applies to recommendation — evidence for RQ3
that the pattern is an architectural property of the system rather than a one-off accommodation of
the LLM boundary.

Confidence is a bounded `double` in [0.0, 1.0] rejecting NaN and infinities at construction, not a
free numeric field: an unbounded confidence makes "most confident" meaningless and would let a
single malformed submission dominate every future resolution.

Assertions carry `CompetencyFrameworkId` as well as `CompetencyId` because proficiency levels are
M1 elements scoped to one framework model — level `L2` in a bespoke curriculum framework and `L2`
in SFIA are unrelated. A level without its framework is uninterpretable, and ADR-011 forbids
reaching across the schema boundary to recover it, so the framework travels with the assertion.
Evidence recorded for a competency under a different framework than the assertion's own is
rejected as a domain error rather than silently accepted, since a competency belongs to exactly
one framework and a mismatch means the caller is confused about which model it is describing.

## Consequences
Every resolved level is traceable to the observations that produced it, so a skill gap computed in
Step 5 can be explained rather than merely reported — which matters directly to RQ3's
explainability claim. Substituting the policy substitutes the system's judgement without touching
storage, the API, or the aggregate. Because evidence is append-only, a profile is an audit trail
by construction.

## Trade-offs
Evidence accumulates without bound within an assertion and this step provides no pruning,
supersession, or revision path — accepted for the slice, with revision recorded below as the
obvious next increment. Resolution runs on write rather than on read, so changing the policy does
not retroactively re-resolve stored assertions; a re-resolution pass would be required, and none
is provided. Confidence is supplied by the caller and unverified, which is appropriate while
evidence is self-reported and would not be once assessments are integrated. The closed enum means
a new evidence type is a code change, which is the intended friction.

## Quality attributes affected
Explainability (+ provenance retained for every resolved level, RQ3), modifiability (+ policy
substitutable in isolation), testability (+ lambda policies isolate aggregate tests from
resolution arithmetic), auditability (+ append-only evidence), storage growth (− unbounded
evidence per assertion), consistency (− stored resolutions do not follow a policy change).

## Amendment 1 — the recording timestamp is system-assigned (Step 4 Phase 3, Accepted 2026-08-03)

This ADR fixed `recordedAt` as part of the evidence record but left unstated who supplies it. Phase 3
settles it: the application service stamps evidence from the injected `Clock`, and
`RecordEvidenceCommand` carries no timestamp field at all.

The reason is not tidiness. The default policy breaks confidence ties **by recency**, so a
caller-supplied timestamp is an input to the system's judgement about that caller's own proficiency:
post-dating a self-declared claim would win the tie-break and raise the resolved level, and nothing
could verify the value. Removing the field removes the lever.

The cost is that `recordedAt` now means *when the system recorded this*, not *when the observation
occurred* — and the example in this ADR's own Decision section ("an SFIA self-assessment from March
2026") describes the latter. That distinction is accepted rather than blurred: should the date an
observation actually occurred become necessary, it arrives as a **separate `observedAt` field**,
caller-supplied and explicitly unverified, leaving `recordedAt` meaning what its name says. Widening
`recordedAt` to carry both meanings would reintroduce the tie-break lever under a different name.

## Future evolution
Evidence revision and supersession — marking a record superseded rather than appending beside it —
is the natural next increment and needs no schema change beyond a flag. A re-resolution service
would close the write-time-resolution gap. Additional policies (most-recent-wins,
corroboration-threshold, weighted-by-evidence-type) are new classes and a bean choice, which is
the point of the port. Should assessment integration arrive, `ASSESSMENT` evidence would carry a
verified confidence supplied by the assessing context rather than the caller.
