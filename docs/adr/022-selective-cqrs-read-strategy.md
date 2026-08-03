# ADR-022: Selective cross-context read strategy — asynchronous projection for competency, synchronous query for learner

Status: Accepted
Date: 2026-08-03

## Problem
Gap Analysis needs two things it does not own: what a target competency model requires, and what a
learner is held to have attained. Today it can obtain neither. Every module's `api` package
publishes identifiers and nothing else — `CompetencyId`, `CompetencyFrameworkId`, `LearnerId` — so
there is no query contract anywhere in the system, and ADR-011 forbids reaching into another
context's schema.

ADR-007 settles half the answer: a compiled competency-model projection owned by Gap Analysis and
rebuilt from integration events. It says nothing about learner attainment, and the temptation is to
treat the two symmetrically — project both, or query both. Symmetry would be the wrong instinct
here, because the two reads have different shapes.

There is also a consequence to face rather than discover. Steps 3 and 4 each added a complete
context with an empty `git diff` under `competency/src/main`. Whatever is chosen, Step 5 ends that:
a producer must announce something, or expose something, before a new consumer can read it.

## Alternatives
1. **Project both.** Competency models and learner attainment both replicated into `gap_analysis`
   via events. Symmetric and fully decoupled at runtime — and it doubles the eventing work,
   duplicates a learner's proficiency into a second context that has no need to own it, and
   contradicts ADR-007, whose scope is a *competency-model* projection specifically.
2. **Query both.** Published synchronous query ports on `competency.api` and `learner.api`. Simple
   and immediately consistent, but it reverses ADR-007 outright, puts EMF-graph traversal on every
   gap computation, and creates the runtime dependency on Competency Modelling that ADR-019 already
   declined for the same reason.
3. **Project competency, query learner** (chosen).

## Decision
Option 3, and the asymmetry is the decision rather than an inconsistency in it.

**Competency Modelling is projected asynchronously.** A registered model is compiled into flat rows
in the `gap_analysis` schema when an integration event announces it. This is ADR-007 realised
literally. The justification is the shape of the data: a competency model is an interpreted M1 EMF
graph, expensive to traverse, changing rarely, and — under ADR-012 — forbidden from crossing the
module boundary as `EObject` at all. Compilation must therefore happen inside Competency Modelling
before publication, and once compiled it may as well be stored.

**Learner Profiling is read synchronously** through a published query contract on `learner.api`
returning attained levels with their evidence provenance. The justification is again the shape:
attainment is small, per-learner, changes often, and is wanted fresh. Projecting it would trade
immediate consistency for nothing — and `gapanalysis` already declares
`allowedDependencies = {"competency :: api", "learner :: api"}`, so a runtime call along that edge
was architecturally sanctioned before this ADR existed. The projection exists for performance, not
because cross-context calls are forbidden.

**Registration, not publication, triggers the projection.** `ModelStatus.PUBLISHED` exists but
nothing sets it, and no publish transition exists. Introducing a model lifecycle is a Competency
Modelling concern with its own authorisation and workflow questions, and adding it here would widen
Step 5's change to the producer for no gain to gap computation. The assumption is recorded plainly:
**a registered competency model is an eligible projection source.** An explicit publication
workflow is future work, and when it arrives the projection filters on state rather than changing
shape — which is why `ModelStatus` was introduced early.

**The zero-touch consequence, stated precisely.** Steps 3 and 4 demonstrated **consumer isolation**:
adding a context that *references* another perturbs it not at all. Step 5 demonstrates something
different and harder — **additive integration cost**: what it takes for an existing producer to
serve a genuinely new consumer. The change to Competency Modelling must be small, purely additive,
and confined to a published contract, and it should be *measured and reported* rather than merely
claimed. An empty diff was the easy case; a small, additive, contract-shaped diff is the case worth
evidencing for RQ2. This ADR does not treat the end of zero-touch as a regression and neither
should any document citing it.

## Consequences
Gap computation reads flat rows for requirements and makes one call for attainment, so no EMF graph
is traversed on the analytical path. Competency Modelling and Gap Analysis share no runtime call,
preserving the extraction story where the data is largest. Learner attainment is always current,
which matters because evidence changes far more often than frameworks do. The module DAG gains no
edge: both dependencies were already declared. And ADR-019's accepted weakness becomes closeable —
once the projection exists, an assertion naming a competency absent from it is detectable where the
data already is.

## Trade-offs
Two read mechanisms coexist, and a reader meeting one will reasonably ask why the other differs;
the answer is the data-shape test above, and this record exists so the question has one. Gap reports
computed from the projection are eventually consistent with the competency model — a gap computed
immediately after a model changes may reflect the previous version, which ADR-007 already accepts.
A synchronous call to Learner Profiling means a gap computation fails if that module is unavailable,
which a projection would have tolerated; acceptable inside one deployable, and a consideration if
the contexts are ever separated. Event infrastructure is introduced for a single consumer, and the
Modulith publication registry is a cross-cutting table with no natural home under ADR-011's
per-module schema rule — it lives in `common`, which ADR-011 Amendment 1 records along with the
test that admits it there.

**Scope of the event mechanism.** Durable publication is adopted for reliability, not as a general
platform. One producer, one consumer, one event type. No event bus, no external broker, no
republication API, no cross-context choreography — the scope stays exactly as wide as the
competency-model projection requires, and widening it is a decision to record rather than to make
incrementally.

## Quality attributes affected
Performance (+ no EMF traversal per computation), freshness (+ attainment read live; − model
projection lags), modularity (+ no new DAG edge; + no schema access across contexts), availability
(− gap computation depends on Learner Profiling at runtime), simplicity (− two read mechanisms,
justified by data shape), evolvability (+ producer change is additive and contract-shaped).

## Future evolution
An explicit publication workflow in Competency Modelling turns the projection's trigger into a
state filter without changing its shape. Should Learner Profiling ever need to be read at a volume
that makes synchronous calls unattractive, its attainment can be projected by the same mechanism
already built for competency — the decision to query rather than project is reversible, and this
ADR is where the reversal would be recorded. If the contexts are ever deployed separately, the
synchronous edge is the one to reconsider first.
