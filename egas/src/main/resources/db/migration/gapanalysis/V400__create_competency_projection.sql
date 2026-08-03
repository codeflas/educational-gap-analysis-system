-- Gap Analysis (V400-V499 range per ADR-011 migration namespacing).
--
-- The compiled competency-model read projection (ADR-007): the one CQRS surface in the system,
-- owned by this context and rebuilt from Competency Modelling integration events. "Compiled" means
-- the interpreted M1 graph arrives already flattened — competencies and proficiency levels as
-- plain queryable rows — so gap computation never traverses a model it would need EMF to read.
--
-- Every row here is DERIVED. Nothing is authored in this schema, and the source of truth remains
-- competency.framework_model. A defect in the projection is repaired by replaying the event, not
-- by migrating data, which is what makes the eventual consistency ADR-007 accepts tolerable.
--
-- framework_id and competency_id are identifier values carried from another context: no foreign
-- key crosses the schema boundary (ADR-011), and their referential validity is not checked here
-- (ADR-019). Foreign keys *within* gap_analysis are mandatory and are what make a projected model
-- a unit that can be replaced wholesale.
--
-- Phase note: the plan's section 5 named a single V400__ carrying both these projection tables and
-- the gap-report tables. They are split, because the gap tables belong to phase 4 and creating
-- them now would ship a schema no code reads. The gap tables land as V401__.
create table gap_analysis.projected_framework (
    framework_id  uuid         primary key,
    name          varchar(200) not null,
    version       varchar(50)  not null,
    -- When the source model was registered, carried from the event, versus when this projection
    -- was written. Keeping both makes projection lag observable rather than a matter of inference.
    registered_at timestamptz  not null,
    projected_at  timestamptz  not null
);

create table gap_analysis.projected_level (
    framework_id uuid         not null
        references gap_analysis.projected_framework (framework_id) on delete cascade,
    code         varchar(50)  not null,
    name         varchar(200),
    ordinal      integer      not null,
    primary key (framework_id, code),
    constraint ck_projected_level_ordinal check (ordinal >= 0)
);

create table gap_analysis.projected_competency (
    competency_id uuid         primary key,
    framework_id  uuid         not null
        references gap_analysis.projected_framework (framework_id) on delete cascade,
    code          varchar(50)  not null,
    name          varchar(200) not null,
    area_code     varchar(50)  not null,
    -- Codes are unique framework-wide by the metamodel's own well-formedness rules; restating it
    -- here means a projection that violated the source model's invariant could not be written.
    -- It also indexes the framework lookup that every gap computation begins with.
    constraint uq_projected_competency_framework_code unique (framework_id, code)
);

-- The levels a competency has a descriptor for — what the model makes AVAILABLE. The metamodel
-- states no required level, so nothing here is a target: a target is supplied per analysis
-- request (ADR-021).
create table gap_analysis.projected_competency_level (
    competency_id uuid        not null
        references gap_analysis.projected_competency (competency_id) on delete cascade,
    level_code    varchar(50) not null,
    primary key (competency_id, level_code)
);
