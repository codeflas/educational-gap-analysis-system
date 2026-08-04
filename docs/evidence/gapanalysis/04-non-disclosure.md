# Evidence 04 — denial is indistinguishable from absence

Captured: 2026-08-04T03:46:45Z · Instance: http://localhost:8080 (dev profile)

A caller who may not read a report receives `404`, not `403`. A `403` would confirm that
the identifier names a real report, turning the endpoint into an enumeration oracle — and
a gap report discloses which learner it is about, so the leak would be worse than for a
profile. The bodies below differ only in the RFC 9457 `instance` member, which echoes the
URI the caller itself supplied and therefore discloses nothing.

## Existing report, caller not permitted to read it
```http
HTTP/1.1 404 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Tue, 04 Aug 2026 03:46:45 GMT

{"type":"about:blank","title":"Gap report not found","status":404,"detail":"No gap report is available for this request.","instance":"/api/gap-reports/58172676-a9a1-4b5b-a2df-4bdb8e37269d"}
```

## Identifier that does not exist, same caller
```http
HTTP/1.1 404 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Tue, 04 Aug 2026 03:46:45 GMT

{"type":"about:blank","title":"Gap report not found","status":404,"detail":"No gap report is available for this request.","instance":"/api/gap-reports/00000000-0000-4000-8000-000000000000"}
```

Expected: identical status, `type`, `title` and `detail`. Asserted mechanically as a byte
comparison in `GapReportApiTests`, which strips `instance` and requires the remainder to
match exactly — a stronger check than eyeballing two transcripts, and the reason this
artefact is corroboration rather than the primary evidence.
