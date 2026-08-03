-- Competency Modelling (V100-V199 range per ADR-011 migration namespacing).
-- Registry of competency framework models (M1): emfjson-serialised content in jsonb plus
-- metadata columns as a construction-time-consistent read projection (list queries never
-- touch 'content'). A GIN index on content is a documented future option once jsonb
-- predicates are actually queried; premature today.
create table competency.framework_model (
    id            uuid         primary key,
    name          varchar(200) not null,
    version       varchar(50)  not null,
    source        varchar(30)  not null,
    status        varchar(20)  not null,
    description   text,
    content       jsonb        not null,
    registered_at timestamptz  not null,
    constraint uq_framework_name_version unique (name, version)
);
