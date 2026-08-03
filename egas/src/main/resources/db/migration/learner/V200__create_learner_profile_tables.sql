-- Learner Profiling (V200-V299 range per ADR-011 migration namespacing).
-- Relational rather than jsonb (ADR-020): a profile's shape is fixed by domain types, not by a
-- metamodel, and the aggregate's central invariant is expressible only as a constraint here.
--
-- Cross-context references (competency_id, framework_id) are identifier values with NO foreign
-- key: ADR-011 prohibits cross-schema keys and ADR-019 accepts the reference as unvalidated.
-- Foreign keys inside this schema are mandatory, and are what make the aggregate a unit.
create table learner.profile (
    id           uuid         primary key,
    auth_subject varchar(200) not null,
    display_name varchar(200) not null,
    created_at   timestamptz  not null,
    -- ADR-017 one-profile-per-principal, made race-free rather than merely checked. The adapter
    -- translates a violation into DuplicateLearnerProfileException, whose javadoc names this
    -- constraint as the authority behind the service's fast-path check.
    constraint uq_learner_auth_subject unique (auth_subject)
);

create table learner.proficiency_assertion (
    id            uuid        primary key,
    profile_id    uuid        not null references learner.profile (id) on delete cascade,
    competency_id uuid        not null,
    framework_id  uuid        not null,
    -- Both representations of the level are stored: Gap Analysis compares ordinals, humans read
    -- codes, and neither is derivable from the other without the competency model that ADR-011
    -- forbids reaching for (ADR-018).
    level_ordinal integer     not null,
    level_code    varchar(50) not null,
    resolved_at   timestamptz not null,
    -- The invariant that justifies the aggregate boundary: at most one assertion per competency.
    -- LearnerProfile.recordEvidence decides it; this constraint makes it unviolatable at rest,
    -- which is what discharges the reconstitute() trust assumption (ADR-020).
    constraint uq_assertion_profile_competency unique (profile_id, competency_id),
    -- Mirrors AttainedLevel's compact constructor, which rejects a negative ordinal. The value
    -- object is the authority; this restates the rule for anything reaching the table by another
    -- route -- a repair script, a future adapter, a manual fix -- and costs nothing at write time.
    constraint ck_assertion_level_ordinal check (level_ordinal >= 0)
);

create table learner.evidence_record (
    id            uuid          primary key,
    assertion_id  uuid          not null references learner.proficiency_assertion (id) on delete cascade,
    type          varchar(30)   not null,
    level_ordinal integer       not null,
    level_code    varchar(50)   not null,
    -- numeric, not double precision: Confidence is a bounded weight in [0.000, 1.000] and an
    -- exact decimal cannot drift, which matters because it is an input to level resolution.
    confidence    numeric(4, 3) not null,
    source        varchar(500)  not null,
    recorded_at   timestamptz   not null,
    -- Evidence is append-only and exposed oldest-first (ADR-018). recorded_at is not unique --
    -- two observations may share an instant -- so insertion order is carried explicitly.
    seq           integer       not null,
    -- Row ids are derived from (assertion_id, seq) rather than generated, so re-saving an
    -- aggregate updates these rows in place instead of inserting a replacement set that would
    -- collide with the rows it is about to orphan. Safe because evidence is append-only.
    constraint uq_evidence_assertion_seq unique (assertion_id, seq),
    -- The two checks below mirror domain value-object invariants rather than inventing rules.
    -- Confidence's compact constructor bounds it to [0.0, 1.0] and rejects NaN and the
    -- infinities; AttainedLevel's rejects a negative ordinal. The value objects remain the
    -- authority -- every write goes through them -- and these restate the same bounds for
    -- anything that reaches the table by another route.
    constraint ck_evidence_confidence check (confidence >= 0 and confidence <= 1),
    constraint ck_evidence_level_ordinal check (level_ordinal >= 0)
);

-- No index on proficiency_assertion(competency_id) yet. Gap Analysis will query that column
-- (ADR-007), but no query does today, and V100 set the precedent of adding indexes when a
-- predicate exists rather than in anticipation of one.
