# ADR-020: Learner state in relational tables; JSONB confined to dynamic model artefacts

Status: Accepted
Date: 2026-08-03

## Problem
ADR-005 fixes persistence as "PostgreSQL + JSONB models", and Step 2 realised it by storing each
M1 competency framework as an emfjson document in a single `jsonb` column beside queryable
metadata. Step 4 Phase 2 must persist a different kind of thing — a learner profile with its
proficiency assertions and their supporting evidence — and the precedent admits two readings.
Either JSONB is the house style for aggregate state, in which case a profile is a document too;
or ADR-005's subject was *models* specifically, in which case the learner context needs its own
answer.

The ambiguity is not merely editorial. ADR-018 places evidence inside the `ProficiencyAssertion`
entity and the assertion inside the `LearnerProfile` aggregate, and the aggregate's committed
documentation states that "the database's unique constraint backs the rule as a second line of
defence" for the *at most one assertion per competency* invariant. Whether that sentence is true
depends entirely on the storage shape chosen here.

*Recording note:* ADR-005 exists in the decision index but its full text has not yet been
transcribed from the design log, so its scope is inferred from the index entry and from the one
place the codebase cites it — `FrameworkModelJpaEntity`, which explains the `jsonb` column as
holding "the emfjson-serialised M1 graph ... queryable-in-place later via jsonb operators/GIN,
unlike an opaque XMI blob". This ADR resolves the ambiguity rather than waiting on that
transcription, and does not restate or alter ADR-005's decision for the competency context.

## Alternatives
1. **A JSONB document per profile**, mirroring the competency slice: metadata columns plus one
   `content` column holding assertions and evidence. Maximum symmetry, one persistence idiom to
   learn, and schema changes to the assertion shape need no migration. But the invariant the
   aggregate exists to protect becomes unenforceable at rest: PostgreSQL cannot express "no two
   elements of this array share a `competency_id`" as a constraint, only as a generated column
   plus an expression index that still cannot state per-element uniqueness. The Phase 1
   documentation would be promising a guarantee the schema does not provide. Gap Analysis's
   central query — which learners hold which competency — becomes a containment predicate over
   documents rather than an indexed lookup, and listing profiles with an assertion count means
   parsing every document to count an array.
2. **A hybrid**: relational `profile` row, with assertions and evidence as one JSONB column. Keeps
   the one-profile-per-subject constraint and reduces the table count, but preserves every problem
   above for the part that actually carries the invariant, while adding a second idiom inside a
   single aggregate.
3. **Relational tables throughout the learner schema** (chosen).

## Decision
Option 3. Learner domain state is persisted as ordinary relational tables in the `learner`
schema: `profile`, `proficiency_assertion`, and `evidence_record`, related by foreign keys
*within* the schema. JSONB remains restricted to dynamic model artefacts — the M1 graphs of the
Competency Modelling context — and is not adopted as a general aggregate-storage idiom.

**The distinguishing property is who defines the shape.** An M1 competency framework's structure
is defined at runtime by the M2 metamodel and differs from one framework to the next; no stable
DDL can express it, and attempting one would defeat the models-at-runtime interpretation that
ADR-003 and ADR-012 exist to preserve. JSONB is the correct answer there precisely because the
schema is data. A learner profile has no such property: its shape is fixed by Java types the
compiler already checks, it varies for no one, and every field is known at design time. Storing
it as a document would buy flexibility that nothing needs and forfeit constraints that something
does.

The concrete consequence is the constraint set. `unique (profile_id, competency_id)` on
`proficiency_assertion` makes the aggregate's central invariant unviolatable at rest, which is
what turns the Phase 1 trust assumption — `LearnerProfile.reconstitute` deliberately does not
re-validate what the store hands it — from an assumption into an enforced guarantee. A second
constraint, `unique (auth_subject)` on `profile`, makes ADR-017's one-profile-per-principal rule
race-free rather than merely checked. ADR-018's append-only evidence ordering is carried by an
explicit sequence column, because `recorded_at` is not unique and the resolution tests already
exercise records sharing an instant.

Foreign keys are used, and used only inside the `learner` schema, which is exactly what ADR-011
requires: cross-schema keys are prohibited, intra-context integrity is mandatory. The
`competency_id` and `framework_id` columns therefore carry no foreign key and no existence check,
per ADR-019 — they remain opaque identifier values, and this decision does not weaken or revisit
that.

## Consequences
The invariant that justifies the aggregate boundary is enforced by the database rather than
asserted by documentation, and the persistence adapter's round-trip tests become a genuine
discharge of the Phase 1 trust boundary rather than a demonstration of good intentions. Gap
Analysis can reach learner proficiency by indexed lookup on `competency_id` when it lands (ADR-007),
without a schema change and without either context calling the other. Profile listings satisfy the
column-only projection discipline inherited from Step 2, since an assertion count is an aggregate
query rather than a document parse. Because `spring.jpa.hibernate.ddl-auto` is `validate`, any
divergence between the mapping and the migration aborts startup, so the schema and the code cannot
drift apart silently.

## Trade-offs
The system now contains two persistence idioms, and a reader who meets the learner schema first
will reasonably ask why the competency context looks nothing like it; that question is the reason
this record exists, and the answer is the shape-definition test above rather than any appeal to
consistency. Proficiency level ordinal and code are denormalised onto both the assertion and its
evidence rows — real duplication, accepted because ADR-018 requires both representations and
ADR-011 forbids reaching across the schema boundary to recover either. Evidence rows grow without
bound, since ADR-018 provides no pruning or supersession; the growth is now visible as row count
rather than hidden as document size, which is an improvement in observability and no improvement
in volume. Changes to the assertion or evidence shape require a migration where a document column
would have absorbed them silently — intended friction, matching the closed evidence-type enum's
rationale. Finally, the aggregate's collections invite an N+1 fetch on profile load unless the
read path uses an explicit fetch strategy; the obligation is real and is discharged in the
adapter, not by this decision.

## Quality attributes affected
Data integrity (+ the aggregate's invariant enforced at rest; + one-profile-per-subject made
race-free), queryability (+ indexed access for the Gap Analysis read path), operability (+ schema
and mapping cannot drift under `ddl-auto: validate`), conceptual integrity (− two persistence
idioms coexist, justified but requiring explanation), modifiability (− shape changes need
migrations), storage growth (− unbounded evidence rows, unchanged in substance from ADR-018),
performance risk (− N+1 on aggregate load unless explicitly managed).

## Future evolution
Evidence supersession (ADR-018's next increment) becomes a nullable flag and a partial index
rather than a document rewrite. An index on `proficiency_assertion(competency_id)` is added when
Gap Analysis's projection actually queries it, following the same "premature today" posture the
competency slice took toward a GIN index on `content`. Should a genuinely dynamic learner artefact
ever arrive — an imported assessment payload whose structure is defined by an external schema
rather than by EGAS types — JSONB is admissible for that column under exactly the test applied
here, and this ADR is the record of what that test is.
