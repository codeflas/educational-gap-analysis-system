-- Gap Analysis (V400-V499 range per ADR-011 migration namespacing), phase 4.
--
-- Stored gap reports (ADR-021). Unlike V400's projection, NOTHING here is derived: a report is
-- authored by this context, it is the artefact everything downstream consumes, and it cannot be
-- rebuilt by replaying an event. Losing these rows loses history, which is precisely what ADR-021
-- decided to keep.
--
-- Relational rather than jsonb, on ADR-020's test of who defines the shape. A report's structure is
-- fixed by Java types the compiler already checks; it varies for no one. The M1 graphs of the
-- Competency Modelling context are the only artefacts whose schema is data, and JSONB stays there.
-- The concrete payoff is the constraint set below: two of these rules are domain invariants that a
-- document column could not express at all.
--
-- learner_id, framework_id and competency_id are identifier values carried from other contexts: no
-- foreign key crosses the schema boundary (ADR-011) and their referential validity is not checked
-- (ADR-019). A report naming a learner or framework that has since been removed remains readable,
-- which the aggregate's own documentation already promises. Foreign keys INSIDE gap_analysis are
-- mandatory and are what make a report a unit that deletes as one.
create table gap_analysis.gap_report (
    id           uuid        primary key,
    learner_id   uuid        not null,
    framework_id uuid        not null,
    -- Load-bearing, not decoration. A report is a true record of the instant it was made and stops
    -- describing the present as soon as evidence changes; nothing here tracks its inputs and no
    -- invalidation exists (ADR-021). Every consumer must read this column as significant.
    generated_at timestamptz not null
);

-- The listing predicate exists in the same commit that adds this index -- reports for one learner,
-- newest first -- so this is not the anticipatory indexing V100 and V200 declined to do.
create index ix_gap_report_learner on gap_analysis.gap_report (learner_id, generated_at desc);

create table gap_analysis.skill_gap (
    id                     uuid         primary key,
    report_id              uuid         not null
        references gap_analysis.gap_report (id) on delete cascade,
    -- The analysis-target snapshot: what this competency was measured against, copied rather than
    -- referenced. competency_id is the derived identity (ADR-019 Amendment 1) that still resolves
    -- to a live competency where one exists; the code and name are copies, so a report computed in
    -- March stays explicable in June after the framework has been revised.
    competency_id          uuid         not null,
    competency_code        varchar(50)  not null,
    -- Nullable, matching AnalysisTarget and the projection's ProjectedCompetency: a name is
    -- descriptive, and a model that omits one is not thereby unanalysable.
    competency_name        varchar(200),
    -- A TARGET, never a requirement. The M2 metamodel states no level anyone must reach; this value
    -- came from the analysis request or defaulted to the highest level defined (ADR-021).
    target_level_code      varchar(50)  not null,
    target_ordinal         integer      not null,
    -- The attainment snapshot, ABSENT AS A WHOLE when nothing has been measured. Three nullable
    -- columns rather than a zero-ordinal sentinel, because "never assessed" and "assessed at the
    -- lowest level" are different problems calling for different remedies -- an assessment versus a
    -- learning intervention -- and collapsing them would erase the distinction most useful to a
    -- recommender (ADR-021).
    attained_ordinal       integer,
    attained_level_code    varchar(50),
    attainment_resolved_at timestamptz,
    -- Decided by a GapSeverityPolicy and stored, never recomputed on read: re-running a policy on
    -- load would silently rewrite a historical judgement whenever the configured rule changed.
    -- Stored by name, never by ordinal, so a reordered enum constant cannot rewrite the meaning of
    -- every historical row. No check constraint enumerates the values, following the precedent
    -- EvidenceType set in V200: the Java enum is the authority and every write passes through it.
    severity               varchar(20)  not null,
    -- The invariant GapReport enforces in generate() AND reconstitute(): at most one finding per
    -- competency. Two findings could disagree on target and severity, and gapFor() would return
    -- whichever came first, surfacing ambiguity as an arbitrary answer. This constraint is what
    -- turns the aggregate's reconstitution check from a guard against a possible state into a
    -- guarantee that the state cannot exist -- the same role uq_assertion_profile_competency plays
    -- for LearnerProfile under ADR-020.
    constraint uq_skill_gap_report_competency unique (report_id, competency_id),
    -- Attainment is all-or-nothing. Without this, a partially-written row could report an attained
    -- ordinal with no level code, which maps to no state the domain can represent: AttainmentSnapshot
    -- requires all three together and SkillGap holds either a whole snapshot or none.
    constraint ck_skill_gap_attainment_complete check (
        (attained_ordinal is null and attained_level_code is null and attainment_resolved_at is null)
        or (attained_ordinal is not null and attained_level_code is not null
            and attainment_resolved_at is not null)),
    -- Mirroring the compact constructors of AnalysisTarget and AttainmentSnapshot, which reject a
    -- negative ordinal. The records remain the authority; these restate the bound for anything
    -- reaching the table by another route.
    constraint ck_skill_gap_target_ordinal check (target_ordinal >= 0),
    constraint ck_skill_gap_attained_ordinal check (attained_ordinal is null or attained_ordinal >= 0)
);

-- The last link of the explainability chain: the observations that supported the attainment, copied
-- into the report when it was computed. Without these rows a report could be displayed but not
-- defended, and RQ3's explainability claim would rest on prose rather than on data.
create table gap_analysis.gap_evidence (
    id                 uuid          primary key,
    skill_gap_id       uuid          not null
        references gap_analysis.skill_gap (id) on delete cascade,
    -- A plain string, not an enum column mirroring learner.evidence_record.type. Learner Profiling
    -- flattens its EvidenceType at its own published boundary, and re-typing it here would assert a
    -- shared vocabulary these contexts deliberately do not have.
    type               varchar(30)   not null,
    claimed_ordinal    integer       not null,
    claimed_level_code varchar(50)   not null,
    -- numeric, not double precision, for the same reason as learner.evidence_record.confidence: an
    -- exact decimal cannot drift, and this value is part of a stored judgement's justification.
    confidence         numeric(4, 3) not null,
    -- Nullable here where the learner column is not. EvidenceSnapshot admits a null source because
    -- it copies whatever arrived; not every observation carries a citable one, and a snapshot that
    -- refused the record would lose the rest of the provenance to save a single field.
    source             varchar(500),
    recorded_at        timestamptz   not null,
    -- Evidence order is meaningful and recorded_at is not unique -- two observations may share an
    -- instant -- so insertion order is carried explicitly, as it is in V200.
    seq                integer       not null,
    -- Row ids are derived from (skill_gap_id, seq) rather than generated. EvidenceSnapshot is a
    -- value object with no identity of its own, and a random id would differ on every save, so
    -- re-saving a report would insert a replacement set that collided with this constraint.
    constraint uq_gap_evidence_skill_gap_seq unique (skill_gap_id, seq),
    -- Mirroring EvidenceSnapshot's compact constructor, which bounds confidence to [0.0, 1.0] and
    -- rejects NaN, and rejects a negative claimed ordinal.
    constraint ck_gap_evidence_confidence check (confidence >= 0 and confidence <= 1),
    constraint ck_gap_evidence_claimed_ordinal check (claimed_ordinal >= 0)
);

-- No constraint ties severity to the presence of attainment, deliberately. UNASSESSED is what the
-- DEFAULT policy answers for an absent attainment, but ADR-021 makes severity substitutable, and an
-- institution may legitimately grade absence as MAJOR or grade a met target as something other than
-- MET. A constraint encoding the default rule would make the port unsubstitutable at the storage
-- tier, which is the one place a policy change must not have to reach.
