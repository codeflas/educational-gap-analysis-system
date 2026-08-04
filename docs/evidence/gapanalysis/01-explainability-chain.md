# Evidence 01 — the explainability chain, end to end

Captured: 2026-08-04T03:46:45Z · Instance: http://localhost:8080 (dev profile)

RQ3 claims the system can *explain* a gap rather than merely report one. That claim is
discharged by data, not by prose: every finding below carries the analysis target it was
measured against, the attainment the learner was held to have reached, and the
observations behind that attainment — each having crossed two module boundaries intact
(ADR-021).

Learner `4dcf2fb8-af5a-4324-a90d-6304abbc8add` · framework `21e3cac1-004c-4153-97e3-e2800026c6a7` · report `58172676-a9a1-4b5b-a2df-4bdb8e37269d`

## The stored report, read back
```json
{"id":"58172676-a9a1-4b5b-a2df-4bdb8e37269d","learnerId":"4dcf2fb8-af5a-4324-a90d-6304abbc8add","frameworkId":"21e3cac1-004c-4153-97e3-e2800026c6a7","generatedAt":"2026-08-04T03:46:45.244875Z","gaps":[{"skillGapId":"964f4e23-b120-4d2e-b633-58913eb4b9d9","competencyId":"d6286dad-419c-3910-8341-8455f0775d94","competencyCode":"SE-DSN","competencyName":"Software Design","targetLevelCode":"L2","targetOrdinal":2,"severity":"UNASSESSED","unassessed":true},{"skillGapId":"cfd37948-b789-4eb3-8ebb-3f5c4f9e99d3","competencyId":"5c37a7bf-e949-3440-bdf2-bca8329addfe","competencyCode":"SE-TST","competencyName":"Software Testing","targetLevelCode":"L1","targetOrdinal":1,"severity":"UNASSESSED","unassessed":true}]}
```

What to read in it:

- `targetLevelCode` / `targetOrdinal` — what the finding was measured against. Supplied by
  the request, or defaulted to the highest level the competency has a descriptor for. It is
  **never** a requirement read from the model: the M2 metamodel states none (ADR-021).
- `attainment.evidence[]` — type, claimed level, confidence, source and timestamp, copied
  into the report when it was computed. Copies, not references, so this report stays
  explicable after the framework is revised or the evidence changes.
- `severity` — decided by the configured `GapSeverityPolicy`, never by the aggregate, and
  stored rather than recomputed on read.
- `generatedAt` — load-bearing. A report is a true record of its instant and stops
  describing the present as soon as evidence changes; nothing invalidates it.
- SE-ARC is **absent** from the findings: the model describes it at no level, so there is
  nothing it could be measured against, and inventing a target would fabricate a
  requirement the model does not state.
