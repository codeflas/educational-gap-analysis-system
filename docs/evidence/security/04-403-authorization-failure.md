# Evidence 04 — 403 authorisation failure

Captured: 2026-08-03T05:54:53Z · Instance: http://localhost:8080 (dev profile)

A LEARNER holds a perfectly valid token and attempts to register a framework. This is
an *authorisation* failure, not an authentication one: the caller is known, and simply
lacks the role. 403, never 401 — conflating the two is the defect ADR-015 guards against.

## LEARNER attempts POST /api/frameworks
```http
HTTP/1.1 403 
WWW-Authenticate: Bearer error="insufficient_scope", error_description="The request requires higher privileges than provided by the access token.", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Mon, 03 Aug 2026 05:54:53 GMT
```

Expected: status `403`. (The same principal receives `200` on `GET /api/frameworks`;
the role matrix is asserted cell by cell in `SecurityAuthorizationTests`.)
