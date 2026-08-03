# Architecture Decision Log

Numbered, immutable records in the extended MADR style (template: `adr-template.md`).
Full texts to be transcribed from the design log; statuses reflect the architecture freeze.

| ADR | Title | Status |
|-----|-------|--------|
| 001 | Architectural style: modular monolith on Spring Modulith | Accepted |
| 002 | MDE realisation: models-at-runtime with first-class transformations | Accepted |
| 003 | Ecore strategy: generated M2 (frozen wk 3), runtime-interpreted M1 | Accepted |
| 004 | Technology stack: Java 21, Boot 3.4, Modulith, PostgreSQL, React/TS | Accepted |
| 005 | Persistence: PostgreSQL + JSONB models + in-memory pathway graph | Accepted |
| 006 | Recommendation: knowledge-based core, Strategy port, ExplanationPort | Accepted |
| 007 | CQRS scope: compiled competency-model read projection only | Accepted |
| 008 | Boundary enforcement: Modulith verify + ArchUnit fitness functions | Accepted |
| 009 | API style: REST + OpenAPI (GraphQL as documented evolution) | Accepted |
| 010 | AuthN/Z: self-issued RSA JWTs via Spring OAuth2 resource server | Accepted |
| 011 | Persistence isolation: schema-per-module + migration ranges | Accepted |
| 012 | EMF confined to Competency Modelling as its domain formalism | Accepted |
| 013 | Token issuance: config-backed dev principals, fail-fast RSA keys | Accepted |
| 014 | Platform web-adapter convention (`platform.infrastructure.web`) | Accepted |
| 015 | Authorisation: centralised URL-pattern rules in one filter chain | Accepted |
| 016 | Identity propagation: caller identity as command data, not ambient state | Accepted |
| 017 | Learner identity mapping: auth subject on the profile, explicit provisioning | Accepted |
| 018 | Evidence-backed proficiency with a substitutable level-resolution policy | Accepted |
| 019 | Cross-context reference integrity: competency identifiers unvalidated | Accepted |

**ADR-010 realisation note (Step 3).** ADR-010 fixed authentication and authorisation as
self-issued RSA JWTs validated by a Spring OAuth2 resource server. It is realised in Step 3 and
elaborated by three records that leave its decision intact: ADR-013 (how tokens are issued, where
principals come from, key policy and claim shape), ADR-014 (where the issuing controller lives),
and ADR-015 (where authorisation is expressed). Rows 001–010 above are index entries whose full
texts are transcribed from the design log; this note records the realisation without altering the
original decision.
