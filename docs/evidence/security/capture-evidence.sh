#!/usr/bin/env bash
# Regenerates the three transcript artefacts of the Step 3 security evidence pack against a
# running development instance. The Swagger screenshot (02) is manual by nature and is not
# produced here.
#
#   Terminal 1:  cd egas && mvn spring-boot:run      # activates the dev profile (see pom.xml)
#   Terminal 2:  ./docs/evidence/security/capture-evidence.sh
#
# Requires: curl, and a JSON pretty-printer is used only if `jq` is present.
#
# The private key is never read, printed, or captured by this script. Signatures do appear in
# the transcripts; a signature is public data, unlike the key that produced it.

set -euo pipefail

BASE_URL="${EGAS_BASE_URL:-http://localhost:8080}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

EDUCATOR_USER="dev-educator"
EDUCATOR_PASS="dev-educator-password"
LEARNER_USER="dev-learner"
LEARNER_PASS="dev-learner-password"

note() { printf '\n=== %s\n' "$1"; }

decode_segment() {
  # base64url -> base64, pad, decode. Portable enough for the two segments we need.
  local seg="$1"
  seg="${seg//-/+}"; seg="${seg//_//}"
  while (( ${#seg} % 4 )); do seg+="="; done
  printf '%s' "$seg" | base64 --decode 2>/dev/null || true
}

require_running() {
  if ! curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
    echo "ERROR: no instance answering at ${BASE_URL}." >&2
    echo "Start one with:  cd egas && mvn spring-boot:run" >&2
    exit 1
  fi
}

capture_issuance() {
  local out="${OUT_DIR}/01-jwt-issuance-flow.md"
  note "01 — JWT issuance flow -> ${out}"

  local body response token header payload
  body="{\"username\":\"${EDUCATOR_USER}\",\"password\":\"${EDUCATOR_PASS}\"}"
  response="$(curl -sS -X POST "${BASE_URL}/auth/token" \
      -H 'Content-Type: application/json' -d "${body}")"
  token="$(printf '%s' "${response}" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
  header="$(decode_segment "$(cut -d. -f1 <<<"${token}")")"
  payload="$(decode_segment "$(cut -d. -f2 <<<"${token}")")"

  {
    echo "# Evidence 01 — JWT issuance flow"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo "Development principal exchanges credentials for an RS256 bearer token (ADR-013)."
    echo "The private key is never displayed; the signature below is public data."
    echo
    echo '## Request'
    echo '```http'
    echo "POST /auth/token HTTP/1.1"
    echo "Content-Type: application/json"
    echo
    echo "{\"username\":\"${EDUCATOR_USER}\",\"password\":\"<dev password, see application.yml>\"}"
    echo '```'
    echo
    echo '## Response'
    echo '```json'
    printf '%s\n' "${response}"
    echo '```'
    echo
    echo '## Decoded token header (base64url segment 1)'
    echo '```json'
    printf '%s\n' "${header}"
    echo '```'
    echo
    echo '## Decoded token claims (base64url segment 2)'
    echo '```json'
    printf '%s\n' "${payload}"
    echo '```'
    echo
    echo 'Expected: `alg` is RS256; claims carry `iss`, `sub`, `iat`, `exp` and `roles`.'
    echo '`aud` and `jti` are deliberately absent — ADR-013 records the rationale for both.'
  } > "${out}"
}

capture_401() {
  local out="${OUT_DIR}/03-401-authentication-failure.md"
  note "03 — 401 authentication failure -> ${out}"

  local no_token bad_token
  no_token="$(curl -sSi "${BASE_URL}/api/frameworks")"
  bad_token="$(curl -sSi "${BASE_URL}/api/frameworks" -H 'Authorization: Bearer not-a-jwt')"

  {
    echo "# Evidence 03 — 401 authentication failure"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo 'A business request without a token, and one with a malformed token. Both are'
    echo '*authentication* failures: 401 with an RFC 6750 `WWW-Authenticate: Bearer` challenge.'
    echo
    echo '## No token'
    echo '```http'
    printf '%s\n' "${no_token}"
    echo '```'
    echo
    echo '## Malformed token'
    echo '```http'
    printf '%s\n' "${bad_token}"
    echo '```'
    echo
    echo 'Expected: status `401`, `WWW-Authenticate: Bearer` present, and `invalid_token` in the'
    echo 'challenge for the malformed case.'
  } > "${out}"
}

capture_403() {
  local out="${OUT_DIR}/04-403-authorization-failure.md"
  note "04 — 403 authorisation failure -> ${out}"

  local learner_token response
  learner_token="$(curl -sS -X POST "${BASE_URL}/auth/token" \
      -H 'Content-Type: application/json' \
      -d "{\"username\":\"${LEARNER_USER}\",\"password\":\"${LEARNER_PASS}\"}" \
      | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"

  response="$(curl -sSi -X POST "${BASE_URL}/api/frameworks" \
      -H "Authorization: Bearer ${learner_token}" \
      -H 'Content-Type: application/json' \
      -d '{"name":"Evidence Attempt","version":"1.0","source":"MANUAL","areas":[]}')"

  {
    echo "# Evidence 04 — 403 authorisation failure"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo 'A LEARNER holds a perfectly valid token and attempts to register a framework. This is'
    echo 'an *authorisation* failure, not an authentication one: the caller is known, and simply'
    echo 'lacks the role. 403, never 401 — conflating the two is the defect ADR-015 guards against.'
    echo
    echo '## LEARNER attempts POST /api/frameworks'
    echo '```http'
    printf '%s\n' "${response}"
    echo '```'
    echo
    echo 'Expected: status `403`. (The same principal receives `200` on `GET /api/frameworks`;'
    echo 'the role matrix is asserted cell by cell in `SecurityAuthorizationTests`.)'
  } > "${out}"
}

require_running
capture_issuance
capture_401
capture_403

note "Done. Generated 01, 03 and 04 in ${OUT_DIR}."
echo "Artefact 02 (Swagger Authorize screenshot) is captured manually — see README.md."
