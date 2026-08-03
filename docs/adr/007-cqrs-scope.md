# ADR-007: CQRS scope — one compiled competency-model read projection, and nothing else

Status: Accepted · **Text RECOVERED 2026-08-03 from repository evidence; comparison against the
original design notes is still outstanding**
Date: (original decision predates the repository; recovered before Step 5)

> **Provenance note — read this before relying on the text below.**
> This ADR was Accepted in the architecture freeze but its full text was never transcribed from
> the design log; row 007 of the index was the only record. Step 5 (Gap Analysis) is governed by
> this decision, so the text has been **reconstructed from repository evidence rather than
> invented**, and every substantive claim below is traceable to one of these sources:
>
> | # | Source | What it establishes |
> |---|--------|---------------------|
> | S1 | ADR index, row 007 | "CQRS scope: compiled competency-model read projection only" |
> | S2 | `gapanalysis/package-info.java` | Gap Analysis "will own the compiled read-side projection of validated models (CQRS-lite, ADR-007), rebuilt from Competency Modelling integration events" |
> | S3 | `FrameworkSummary` javadoc | In-module column selection is "NOT the ADR-007 CQRS projection (which is a cross-module, event-rebuilt structure owned by Gap Analysis)" |
> | S4 | `ModelStatus` javadoc | The `publish` transition and its `ModelPublished` integration event "feeding the Gap Analysis read projection, ADR-007" arrive in W6 |
> | S5 | ADR-019 §Alternatives, §Decision, §Future evolution | The projection is "of published competency models rebuilt from integration events"; validation belongs there; scheduled W6 |
> | S6 | Step 4 plan §7 | "ADR-007 fixes CQRS scope to the Gap Analysis read projection only, and inventing a second CQRS surface here would contradict it" |
> | S7 | Step 2 completion review §Deferred | "publish transition + `ModelPublished` event + Gap Analysis projection (W6, ADR-007)" |
> | S8 | `egas/README.md` | gapanalysis — "typed gaps, compiled read projection" |
>
> **No decision has been added that the sources do not support.** Where the sources are silent —
> notably on projection storage shape, rebuild mechanics, and event delivery details — this text
> says so explicitly rather than filling the gap. Those points are settled by ADR-022 and by
> implementation decisions recorded with the step, both of which cite this record as their
> constraint.
>
> **Reconciliation with the original design notes has not yet been performed.** Until it is, this
> text should be read as the best reconstruction the repository supports rather than as a
> transcription. Should the original differ, this file is the one to correct — three later ADRs
> (019, 021, 022) already cite it, so a divergence would propagate.

## Problem
A modular monolith reading across context boundaries has a choice to make once, or it makes it
repeatedly and inconsistently. Gap Analysis must compare a learner's attainment against a target
competency model, but a competency model is an interpreted M1 EMF graph (ADR-003) stored as a jsonb
document (ADR-005) inside another context's schema, which ADR-011 forbids reaching into. Computing
a gap by traversing that graph per request would be expensive and would couple the two contexts at
runtime.

The general answer — separate read models from write models wherever queries are awkward — is
CQRS, and it is a technique that spreads. Applied without a boundary it produces a second model for
every listing, a query bus, projection infrastructure per context, and an eventual-consistency
story the reader must hold in mind everywhere. For a single-tenant dissertation prototype that cost
is disproportionate, and it would obscure rather than demonstrate the architectural claims (S6).

## Alternatives
1. **No read side at all** — Gap Analysis traverses the competency model on demand through a
   published query port. Simplest, immediately consistent, and it creates exactly the synchronous
   cross-context runtime dependency the architecture otherwise avoids; the cost is paid on every
   gap computation rather than once per model change.
2. **CQRS applied generally** — a read model and projection per context wherever a query is
   awkward. Consistent as a principle, disproportionate in practice, and it would multiply
   eventual-consistency surfaces across contexts that have no query problem (S6).
3. **CQRS confined to one place: a compiled competency-model projection owned by Gap Analysis,
   rebuilt from Competency Modelling integration events** (chosen — S1, S2).

## Decision
Option 3. CQRS is admitted **once**, for the one query that genuinely warrants it, and is closed to
further expansion.

**Gap Analysis owns a compiled read projection of competency models** (S1, S2, S8). "Compiled"
means the interpreted M1 graph is flattened into a structure suited to gap computation —
competencies and their proficiency levels as plain queryable rows — rather than replicated as a
document requiring EMF interpretation on the read path. The projection lives in the `gap_analysis`
schema and is owned by that context; no other module reads it (S2).

**It is rebuilt from integration events, not from synchronous calls** (S2, S5). Competency
Modelling emits an integration event when a model becomes eligible; Gap Analysis consumes it and
updates the projection. Neither context calls the other at runtime, so the extraction story of
ADR-001 and the coupling evidence for RQ2 remain intact (S5).

**This is the only CQRS surface in the system** (S1, S6). Other read optimisations are explicitly
*not* instances of it: `FrameworkSummary` and the interface projections behind framework and
learner listings are lazy column selection *inside* a module, chosen to avoid loading content on a
list path, and the codebase states this distinction where the temptation to conflate them is
greatest (S3). No query bus, no second read model, no projection infrastructure elsewhere (S6).

**Scheduling.** The event and its projection were deferred to W6 with Gap Analysis, and
`ModelStatus.PUBLISHED` was introduced early precisely so their arrival would need no schema
migration (S4, S7).

**What the sources do not settle**, and what this ADR therefore does not claim: the projection's
storage shape, its rebuild-from-scratch mechanics, the delivery guarantees of the event channel,
and which model lifecycle state triggers publication. ADR-022 settles these for Step 5 and cites
this record as its constraint.

## Consequences
Gap computation reads flat rows in its own schema rather than interpreting another context's model
graph, so the analytical core is not paced by EMF traversal. The two contexts share no runtime call
and no schema, so either could be extracted without unpicking the other. Because the projection is
derived, it is disposable: a defect in it is repaired by rebuilding rather than by migration.
Confining CQRS to one place keeps the rest of the system single-model, which is what allows every
other context to be read without an eventual-consistency caveat.

## Trade-offs
The projection is eventually consistent with the competency model: a gap computed immediately after
a model changes may reflect the previous version. Accepted, and appropriate — a competency
framework is a slow-moving artefact. The system carries event infrastructure it uses in exactly one
place, which is a fixed cost for a single benefit. And "CQRS only here" is a rule that must be
defended each time another awkward query appears; the codebase already shows the defence being made
in-line (S3, S6), which is the practice this decision depends on.

## Quality attributes affected
Performance (+ gap computation reads flat rows, not an interpreted graph), modularity (+ no runtime
call or schema access between contexts; + extraction preserved), simplicity (+ one read model
rather than a general pattern; − event infrastructure for a single consumer), consistency
(− projection lags its source), operability (+ a derived projection is rebuildable rather than
migratable).

## Future evolution
Reference validation becomes a report over the projection once it exists — an assertion naming a
competency absent from the published model is detectable where the data already is, which is the
mechanism ADR-019 records as retiring its accepted weakness (S5). Should a second context ever need
a genuinely different read shape, this ADR is the record to amend rather than to work around,
because the value of "CQRS only here" is entirely in its being enforced.
