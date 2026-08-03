-- One PostgreSQL schema per application module (persistence isolation; ADR-011, proposed).
--
-- Rule: NO cross-schema foreign keys, ever. Cross-context references are by identifier value
-- only, mirroring the Spring Modulith boundaries at the persistence tier. Consequences:
--   + module extraction to a separate service remains a data-migration, not a redesign
--   + coupling evidence for RQ2 holds at the storage layer, not just in Java packages
--   -  no database-enforced referential integrity ACROSS contexts (within a context it is
--      mandatory); cross-context consistency is an application/event concern by design.

create schema if not exists competency;
create schema if not exists learner;
create schema if not exists catalogue;
create schema if not exists gap_analysis;
create schema if not exists recommendation;
