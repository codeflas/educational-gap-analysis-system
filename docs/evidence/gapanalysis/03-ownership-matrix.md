# Evidence 03 — ownership authorisation matrix

Captured: 2026-08-04T03:46:45Z · Instance: http://localhost:8080 (dev profile)

Every cell of the ADR-015 Amendment 2 matrix, executed against a live instance with real
RS256 tokens. The filter chain carries one rule — `/api/gap-reports/** authenticated()` —
and **no role rule at all**, because every operation here is learner-scoped and that is
precisely the predicate a URL pattern cannot express. Ownership is decided in
`GapAnalysisService`, against the caller resolved through `learner.api`.

Report under test: `58172676-a9a1-4b5b-a2df-4bdb8e37269d`, about learner `4dcf2fb8-af5a-4324-a90d-6304abbc8add` (`dev-educator`).
Intruder: `dev-learner` — a LEARNER, so unprivileged, and not the owner.

| # | Caller | Request | Expected | Observed |
|---|--------|---------|----------|----------|
| 1 | dev-educator (owner) | GET /api/gap-reports/{id} | 200 | 200 |
| 2 | dev-learner (unprivileged non-owner) | GET /api/gap-reports/{id} | 404 | 404 |
| 3 | dev-admin (privileged) | GET /api/gap-reports/{id} | 200 | 200 |
| 4 | dev-educator (owner) | GET /api/gap-reports?learnerId=self | 200 | 200 |
| 5 | dev-learner (unprivileged non-owner) | GET /api/gap-reports?learnerId=owner | 403 | 403 |
| 6 | dev-admin (privileged) | GET /api/gap-reports?learnerId=owner | 200 | 200 |
| 7 | (none) | GET /api/gap-reports/{id} | 401 | 401 |

**Rows 2 and 5 are the pair that matters.** Both refuse the same caller, and they differ
deliberately. Row 2 answers `404` because a lookup happened and `403` would confirm that
the identifier names a real report — which also names the learner it is about. Row 5
answers `403` because the learner identifier came from the caller and is never looked up
(ADR-019), so refusing discloses nothing, and a `404` would cost a usable diagnostic for
no privacy gain. Row 7 is the filter chain, not the application layer.
