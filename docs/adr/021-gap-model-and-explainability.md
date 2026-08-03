# ADR-021: Skill-gap model, severity semantics, and the explainability chain

Status: Accepted
Date: 2026-08-03

## Problem
Gap Analysis is the dissertation's core domain: it is where a competency model and a learner
profile meet and produce the artefact everything downstream consumes. Recommendation (ADR-006)
synthesises pathways from gaps, and RQ3 claims the system can *explain* what it recommends. That
claim is only as good as what a gap carries. A gap reduced to "competency X: short by 2" is
sufficient to sort a pathway and insufficient to justify one — the reader cannot see what was
required, what was attained, or on what evidence.

Three questions follow. What is a gap made of; what does its severity mean and who decides; and
what must be retained so an explanation can be produced without re-deriving it.

## Alternatives
1. **Gap as a computed number, nothing stored.** Smallest slice: subtract attained ordinal from
   required ordinal on demand. But a gap becomes irreproducible the moment either input changes,
   there is no artefact to address (`SkillGapId` exists in the published API and implies one), and
   an explanation must be recomputed from data that has since moved.
2. **Gap as a stored number.** Reproducible and addressable, but it records the conclusion without
   the premises: a report can be shown but not defended, and RQ3's explainability claim rests on
   prose rather than data.
3. **Gap as a stored, self-contained finding** — requirement snapshot, attainment snapshot,
   evidence provenance, computed severity, and the references and instant that produced it
   (chosen).

## Decision
Option 3. `GapReport` is a stored aggregate, and it is **self-contained by design**: everything
needed to explain a finding is captured at computation time rather than recovered afterwards.

A report holds the learner reference, the target framework reference, the instant it was generated,
and its gaps. Each `SkillGap` carries the **requirement snapshot** (the competency and the
proficiency level the target model demands), the **attainment snapshot** (the level the learner was
held to have reached, or its absence), the **evidence provenance** behind that attainment, and the
computed **severity**.

Snapshots are copies, not references. That is the point of the decision: a report computed in March
must still be explicable in June after the framework has been revised and further evidence
recorded. A report holding pointers would silently re-narrate itself as its inputs moved, and a
historical analysis built on it would be unsound. Because the copies are taken at computation time
and the instant is recorded, a report is reproducible in the only sense that matters — it says what
was true when it was made.

**Severity is a substitutable policy, not arithmetic.** `GapSeverityPolicy` is a domain port with
one default implementation; the aggregate holds the finding, the port decides what the finding
means. Ordinal distance is the obvious default and is not the only defensible rule: an institution
may treat any absence of evidence as more serious than a one-level shortfall, or weight gaps by
the confidence of the evidence behind them. This is the third independent instance of the
substitutable-strategy technique in the system — ADR-006 applies it to recommendation, ADR-018 to
level resolution — which is what makes it an architectural property of EGAS rather than a
convenience adopted once.

**Absent attainment is a first-class outcome, not a zero.** A learner with no evidence for a
required competency is materially different from one assessed at the lowest level, and collapsing
the two would hide the distinction most useful to a recommendation. The model represents absence
explicitly and lets the severity policy decide what it is worth.

**The explainability chain is the retained structure**: requirement → attainment → the evidence
records that produced it, each already carrying type, claimed level, confidence, source and
timestamp under ADR-018. Because learner evidence is append-only, the provenance a report captures
is a faithful record of what supported the claim at that instant. RQ3's claim is discharged by this
chain being present in the data, not by a narration layer above it.

## Consequences
A gap report can be defended, not merely displayed: every number in it is accompanied by the
requirement it was measured against and the observations that supported it. Recommendation can
synthesise pathways from gaps without reaching back into Learner Profiling or Competency Modelling,
because a report is complete on its own — which keeps the module DAG acyclic and the contexts
independently extractable. Historical analysis becomes possible at all, since reports do not
mutate as their inputs change.

## Trade-offs
Reports duplicate data that also lives in the learner and competency contexts, and that duplication
grows with every computation — the storage cost of reproducibility, accepted deliberately. A report
can become *stale*: it remains a true record of its instant while ceasing to describe the present,
so any consumer must treat the generated timestamp as significant rather than incidental. There is
no automatic invalidation when evidence changes, and none is provided in this step; recomputation is
an explicit act, for the same reason provisioning is in ADR-017. Snapshot copies also mean a
correction to a framework does not propagate to reports already made, which is correct for the
historical record and surprising to anyone expecting a live view.

## Quality attributes affected
Explainability (+ the full chain retained in data, RQ3), reproducibility (+ reports are stable
against input drift), modifiability (+ severity substitutable in isolation), analysability
(+ historical comparison possible), storage growth (− snapshots duplicate source data), freshness
(− reports do not track their inputs; staleness is the caller's concern).

## Future evolution
Recomputation triggered by evidence change becomes possible once Learner Profiling emits an event;
the natural shape is a stale-report report rather than automatic invalidation, so history is
preserved. Additional severity policies — confidence-weighted, prerequisite-aware, time-decayed —
are new classes and a bean choice, which is the point of the port. Should reports need to be
compared across time, the stored snapshots are already the right substrate for a trend view without
any change to how they are produced.
