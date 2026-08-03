# Evidence 03 — 401 authentication failure

Captured: 2026-08-03T05:54:53Z · Instance: http://localhost:8080 (dev profile)

A business request without a token, and one with a malformed token. Both are
*authentication* failures: 401 with an RFC 6750 `WWW-Authenticate: Bearer` challenge.

## No token
```http
HTTP/1.1 401 
WWW-Authenticate: Bearer
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Mon, 03 Aug 2026 05:54:53 GMT
```

## Malformed token
```http
HTTP/1.1 401 
WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Mon, 03 Aug 2026 05:54:53 GMT
```

Expected: status `401`, `WWW-Authenticate: Bearer` present, and `invalid_token` in the
challenge for the malformed case.
