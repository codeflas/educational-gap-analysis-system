# Evidence 02 — unassessed is distinguishable from attained-at-zero

Captured: 2026-08-04T03:46:45Z · Instance: http://localhost:8080 (dev profile)

The distinction ADR-021 exists to protect. "Nothing has been measured" and "measured and
far behind" are different problems calling for different remedies — an assessment versus a
learning intervention — and a recommender that conflated them would propose learning where
an assessment is what is missing.

It is preserved independently at three layers, so no single one is load-bearing:

| Layer | How absence is represented |
|-------|----------------------------|
| Domain | `SkillGap` holds no `AttainmentSnapshot` at all — not a zero-ordinal one |
| Storage | three null columns, held together by `ck_skill_gap_attainment_complete` (V401) |
| Wire | the `attainment` and `shortfall` members are omitted, not zeroed |

## The report: SE-DSN has evidence, SE-TST has none
```json
{"id":"58172676-a9a1-4b5b-a2df-4bdb8e37269d","learnerId":"4dcf2fb8-af5a-4324-a90d-6304abbc8add","frameworkId":"21e3cac1-004c-4153-97e3-e2800026c6a7","generatedAt":"2026-08-04T03:46:45.244875Z","gaps":[{"skillGapId":"964f4e23-b120-4d2e-b633-58913eb4b9d9","competencyId":"d6286dad-419c-3910-8341-8455f0775d94","competencyCode":"SE-DSN","competencyName":"Software Design","targetLevelCode":"L2","targetOrdinal":2,"severity":"UNASSESSED","unassessed":true},{"skillGapId":"cfd37948-b789-4eb3-8ebb-3f5c4f9e99d3","competencyId":"5c37a7bf-e949-3440-bdf2-bca8329addfe","competencyCode":"SE-TST","competencyName":"Software Testing","targetLevelCode":"L1","targetOrdinal":1,"severity":"UNASSESSED","unassessed":true}]}
```

Expected: the SE-TST finding carries `"unassessed": true` and `"severity": "UNASSESSED"`
and has **no** `attainment` or `shortfall` member at all, while SE-DSN carries both.
Asserted mechanically in `GapReportApiTests` and, at the storage tier, in
`JpaGapReportRepositoryTests`.
