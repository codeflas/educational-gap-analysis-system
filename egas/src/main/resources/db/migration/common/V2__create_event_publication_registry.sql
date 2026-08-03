-- Spring Modulith durable event publication registry (ADR-007, ADR-022).
--
-- Lives in `common` under ADR-011 Amendment 1: it holds framework-managed metadata that no module
-- could own — no domain object corresponds to a row, and on extraction each service would simply
-- create its own. Placing it in a business schema would make one context the custodian of another
-- context's delivery state, the precise coupling ADR-011 exists to prevent. It references nothing
-- and nothing references it, so the no-cross-schema-foreign-keys rule is untouched.
--
-- The column shape mirrors Spring Modulith's JPA entity, which carries no @Table schema and so
-- resolves against Hibernate's default schema — set to `common` in application.yml precisely so
-- this table is found here rather than in `public`. The shape below was taken from Hibernate's own
-- generated DDL for that entity rather than transcribed from documentation, so `ddl-auto: validate`
-- has something exact to check.
--
-- One deliberate deviation: `serialized_event` is `text`, not the `varchar(255)` Hibernate would
-- generate for an unannotated String. A serialised CompetencyModelRegistered carries a whole
-- compiled model and exceeds 255 characters immediately; 255 would fail on the first real event.

-- V1 created a schema per application module. `common` is not one of those: it is introduced here,
-- with the first piece of infrastructure metadata that belongs to no module (ADR-011 Amendment 1).
create schema if not exists common;

create table common.event_publication (
    id               uuid not null,
    listener_id      varchar(255),
    event_type       varchar(255),
    serialized_event text,
    publication_date timestamp(6) with time zone,
    completion_date  timestamp(6) with time zone,
    primary key (id)
);

-- Incomplete publications are the ones Modulith resubmits, so they are the rows actually queried.
create index ix_event_publication_incomplete
    on common.event_publication (completion_date)
    where completion_date is null;

-- Completion is looked up by listener and event, the pair Modulith uses to mark a delivery done.
create index ix_event_publication_listener
    on common.event_publication (listener_id, serialized_event);
