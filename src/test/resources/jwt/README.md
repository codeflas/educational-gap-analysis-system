# JWT test fixtures — TEST-ONLY key material

The RSA keys in this directory exist solely for automated tests of the JWT key-handling
policy (ADR-013 / amendment A5). They authenticate nothing, protect nothing, and must never
be used in any deployment.

| File | Purpose |
|---|---|
| `test-private.pem` | PKCS#8 private key (`BEGIN PRIVATE KEY`) of test pair A |
| `test-public.pem`  | X.509 SPKI public key (`BEGIN PUBLIC KEY`) of test pair A |
| `other-public.pem` | Public key of an unrelated pair B — mismatched-pair test case |
| `invalid-key.pem`  | Deliberately unparseable content — parse-failure test case |

Regenerate at any time (same commands as real key provisioning; formats verified by the
Step 3 key-handling spike):

    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out test-private.pem
    openssl pkey -in test-private.pem -pubout -out test-public.pem
