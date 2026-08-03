# Evidence 01 — JWT issuance flow

Captured: 2026-08-03T05:54:53Z · Instance: http://localhost:8080 (dev profile)

Development principal exchanges credentials for an RS256 bearer token (ADR-013).
The private key is never displayed; the signature below is public data.

## Request
```http
POST /auth/token HTTP/1.1
Content-Type: application/json

{"username":"dev-educator","password":"<dev password, see application.yml>"}
```

## Response
```json
{"access_token":"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJlZ2FzIiwic3ViIjoiZGV2LWVkdWNhdG9yIiwiZXhwIjoxNzg1NzQwMDkyLCJpYXQiOjE3ODU3MzY0OTIsInJvbGVzIjpbIkVEVUNBVE9SIl19.HW6KJ7uLUczK-ORfNNV4y_noCFdReyCe9oEcSeCAannZUp5hVAG3R-ILzrSNMm_LPYb4MVdLc0XPWKqr1XWCkY4Qsusq7139SzzBTaIw4hyEIAO3zxlYbN66_mSyZskAqS7n37V1BiTh1eTMMKT6bcN_i6YB94V0p9JPE8t66Hy-tWDwGeztCm1iQ7Wr8ZDfXpVw8Ck8zIzp7_O8poxin8eCMP0D34-hEP8YZjekSgNk08V3uHPuc1vw2R4QlEwNxj79Iow8vHDCVs7JsmLAjHhvrI20IF83O719i0CF4tSG0sBsD0UiVR0X8SU0Oe5x7tYgohk3lPheFr28in22rA","token_type":"Bearer","expires_in":3600}
```

## Decoded token header (base64url segment 1)
```json
{"alg":"RS256"}
```

## Decoded token claims (base64url segment 2)
```json
{"iss":"egas","sub":"dev-educator","exp":1785740092,"iat":1785736492,"roles":["EDUCATOR"]}
```

Expected: `alg` is RS256; claims carry `iss`, `sub`, `iat`, `exp` and `roles`.
`aud` and `jti` are deliberately absent — ADR-013 records the rationale for both.
