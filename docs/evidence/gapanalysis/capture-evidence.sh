#!/usr/bin/env bash
# Regenerates the Step 5 gap-analysis evidence pack against a running development instance.
#
#   Terminal 1:  cd egas && mvn spring-boot:run      # activates the dev profile (see pom.xml)
#   Terminal 2:  ./docs/evidence/gapanalysis/capture-evidence.sh
#
# Artefact 05 (integration cost) needs only a git checkout and is produced regardless.
#
# Every transcript below is produced by executing the system or interrogating the repository, not by
# describing either. Where a claim could be made in prose instead, that is deliberate: a generated
# artefact cannot drift away from the behaviour it documents, and a written one can.
#
# PRINCIPAL NOTE. application-dev.yml defines exactly three principals: dev-learner (LEARNER),
# dev-educator (EDUCATOR) and dev-admin (ADMIN). The ownership matrix needs an unprivileged caller
# who is NOT the owner, and there is no second LEARNER, so the report under test is owned by
# dev-educator and dev-learner plays the intruder. Privileged reading is then demonstrated with
# dev-admin, whose access comes from role rather than ownership. No principal is invented here; the
# roster is what it is, and this is the arrangement it admits.

set -euo pipefail

BASE_URL="${EGAS_BASE_URL:-http://localhost:8080}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${OUT_DIR}/../../.." && pwd)"

OWNER_USER="dev-educator";    OWNER_PASS="dev-educator-password"
INTRUDER_USER="dev-learner";  INTRUDER_PASS="dev-learner-password"
ADMIN_USER="dev-admin";       ADMIN_PASS="dev-admin-password"

# Step 4 baseline — the last commit in which competency/src/main was untouched.
STEP4_END="905b3b7"

note() { printf '\n=== %s\n' "$1"; }

require_running() {
  if ! curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
    echo "ERROR: no instance answering at ${BASE_URL}." >&2
    echo "Start one with:  cd egas && mvn spring-boot:run" >&2
    exit 1
  fi
}

token_for() {
  curl -sS -X POST "${BASE_URL}/auth/token" \
      -H 'Content-Type: application/json' \
      -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

status_of() {
  curl -sS -o /dev/null -w '%{http_code}' -X "$2" "${BASE_URL}$3" -H "Authorization: Bearer $1"
}

first_json_field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -n 1; }

# The canonical framework shape used throughout the suite: three levels, three competencies, with
# SE-DSN described at L2, SE-TST at L1 and SE-ARC described at no level at all. That last one is
# deliberate — a competency the model describes nowhere has no target and is not analysable, which
# the report below demonstrates rather than asserts.
framework_payload() {
  cat <<'JSON'
{
  "name": "Evidence Framework",
  "version": "1.0",
  "description": "Bespoke competency framework used to generate the Step 5 evidence pack.",
  "source": "BESPOKE",
  "levels": [
    {"code": "L1", "name": "Foundation",   "ordinal": 1},
    {"code": "L2", "name": "Intermediate", "ordinal": 2},
    {"code": "L3", "name": "Advanced",     "ordinal": 3}
  ],
  "areas": [
    {"code": "DES", "name": "Design", "description": "Design and architecture",
     "competencies": [
       {"code": "SE-DSN", "name": "Software Design",
        "description": "Designs maintainable software structures.",
        "levelDescriptors": [{"levelCode": "L2", "descriptor": "Applies established design patterns."}]},
       {"code": "SE-ARC", "name": "Software Architecture",
        "description": "Shapes system-level structure and trade-offs.",
        "prerequisites": ["SE-DSN"], "levelDescriptors": []}
     ]},
    {"code": "QUA", "name": "Quality", "description": "Verification and validation",
     "competencies": [
       {"code": "SE-TST", "name": "Software Testing", "prerequisites": ["SE-DSN"],
        "levelDescriptors": [{"levelCode": "L1", "descriptor": "Writes unit tests for own code."}]}
     ]}
  ]
}
JSON
}

# Registers the evidence framework and waits for its projection. The wait is the point rather than
# an inconvenience: projection is asynchronous by ADR-007, and a POST /api/gap-reports issued before
# it lands answers 422 by design (see artefact 01).
register_framework() {
  local educator="$1" id
  id="$(framework_payload | curl -sS -X POST "${BASE_URL}/api/frameworks" \
        -H "Authorization: Bearer ${educator}" -H 'Content-Type: application/json' \
        --data-binary @- | first_json_field id)"

  if [ -z "${id}" ]; then
    # Already registered on a previous run: uq_framework_name_version rejects the duplicate, so
    # fall back to the existing one rather than failing the capture.
    id="$(curl -sS "${BASE_URL}/api/frameworks" -H "Authorization: Bearer ${educator}" \
          | first_json_field id)"
  fi
  echo "${id}"
}

provision() {
  local token="$1" name="$2"
  curl -sS -X POST "${BASE_URL}/api/learners/me" \
      -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
      -d "{\"displayName\":\"${name}\"}" >/dev/null 2>&1 || true
  curl -sS "${BASE_URL}/api/learners/me" -H "Authorization: Bearer ${token}" | first_json_field id
}

record_evidence() {
  local token="$1" competency_id="$2" framework_id="$3" ordinal="$4" code="$5" confidence="$6"
  curl -sS -X POST "${BASE_URL}/api/learners/me/evidence" \
      -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
      -d "{\"competencyId\":\"${competency_id}\",\"competencyFrameworkId\":\"${framework_id}\",
           \"type\":\"SELF_DECLARED\",\"claimedOrdinal\":${ordinal},\"claimedLevelCode\":\"${code}\",
           \"confidence\":${confidence},\"source\":\"evidence capture\"}" >/dev/null
}

# The derived competency identity (ADR-019 Amendment 1), read from the framework detail response
# rather than recomputed here — the whole point of exposing it is that a client need not derive it.
competency_id_for() {
  local token="$1" framework_id="$2" code="$3"
  curl -sS "${BASE_URL}/api/frameworks/${framework_id}" -H "Authorization: Bearer ${token}" \
    | tr ',' '\n' | grep -B2 "\"code\":\"${code}\"" | first_json_field competencyId
}

setup() {
  OWNER_TOKEN="$(token_for "${OWNER_USER}" "${OWNER_PASS}")"
  INTRUDER_TOKEN="$(token_for "${INTRUDER_USER}" "${INTRUDER_PASS}")"
  ADMIN_TOKEN="$(token_for "${ADMIN_USER}" "${ADMIN_PASS}")"

  FRAMEWORK_ID="$(register_framework "${OWNER_TOKEN}")"
  OWNER_LEARNER_ID="$(provision "${OWNER_TOKEN}" "Evidence Owner")"
  provision "${INTRUDER_TOKEN}" "Evidence Intruder" >/dev/null

  local design
  design="$(competency_id_for "${OWNER_TOKEN}" "${FRAMEWORK_ID}" "SE-DSN")"
  [ -n "${design}" ] && record_evidence "${OWNER_TOKEN}" "${design}" "${FRAMEWORK_ID}" 1 "L1" 0.9

  REPORT_ID="$(curl -sS -X POST "${BASE_URL}/api/gap-reports" \
      -H "Authorization: Bearer ${OWNER_TOKEN}" -H 'Content-Type: application/json' \
      -d "{\"learnerId\":\"${OWNER_LEARNER_ID}\",\"frameworkId\":\"${FRAMEWORK_ID}\"}" \
    | first_json_field id)"
}

capture_explainability() {
  local out="${OUT_DIR}/01-explainability-chain.md"
  note "01 — explainability chain -> ${out}"

  {
    echo "# Evidence 01 — the explainability chain, end to end"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo 'RQ3 claims the system can *explain* a gap rather than merely report one. That claim is'
    echo 'discharged by data, not by prose: every finding below carries the analysis target it was'
    echo 'measured against, the attainment the learner was held to have reached, and the'
    echo 'observations behind that attainment — each having crossed two module boundaries intact'
    echo '(ADR-021).'
    echo
    echo "Learner \`${OWNER_LEARNER_ID}\` · framework \`${FRAMEWORK_ID}\` · report \`${REPORT_ID}\`"
    echo
    echo '## The stored report, read back'
    echo '```json'
    curl -sS "${BASE_URL}/api/gap-reports/${REPORT_ID}" -H "Authorization: Bearer ${OWNER_TOKEN}"
    echo
    echo '```'
    echo
    echo 'What to read in it:'
    echo
    echo '- `targetLevelCode` / `targetOrdinal` — what the finding was measured against. Supplied by'
    echo '  the request, or defaulted to the highest level the competency has a descriptor for. It is'
    echo '  **never** a requirement read from the model: the M2 metamodel states none (ADR-021).'
    echo '- `attainment.evidence[]` — type, claimed level, confidence, source and timestamp, copied'
    echo '  into the report when it was computed. Copies, not references, so this report stays'
    echo '  explicable after the framework is revised or the evidence changes.'
    echo '- `severity` — decided by the configured `GapSeverityPolicy`, never by the aggregate, and'
    echo '  stored rather than recomputed on read.'
    echo '- `generatedAt` — load-bearing. A report is a true record of its instant and stops'
    echo '  describing the present as soon as evidence changes; nothing invalidates it.'
    echo '- SE-ARC is **absent** from the findings: the model describes it at no level, so there is'
    echo '  nothing it could be measured against, and inventing a target would fabricate a'
    echo '  requirement the model does not state.'
  } > "${out}"
}

capture_absence() {
  local out="${OUT_DIR}/02-absence-is-not-zero.md"
  note "02 — absence is not zero -> ${out}"

  {
    echo "# Evidence 02 — unassessed is distinguishable from attained-at-zero"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo 'The distinction ADR-021 exists to protect. "Nothing has been measured" and "measured and'
    echo 'far behind" are different problems calling for different remedies — an assessment versus a'
    echo 'learning intervention — and a recommender that conflated them would propose learning where'
    echo 'an assessment is what is missing.'
    echo
    echo 'It is preserved independently at three layers, so no single one is load-bearing:'
    echo
    echo '| Layer | How absence is represented |'
    echo '|-------|----------------------------|'
    echo '| Domain | `SkillGap` holds no `AttainmentSnapshot` at all — not a zero-ordinal one |'
    echo '| Storage | three null columns, held together by `ck_skill_gap_attainment_complete` (V401) |'
    echo '| Wire | the `attainment` and `shortfall` members are omitted, not zeroed |'
    echo
    echo '## The report: SE-DSN has evidence, SE-TST has none'
    echo '```json'
    curl -sS "${BASE_URL}/api/gap-reports/${REPORT_ID}" -H "Authorization: Bearer ${OWNER_TOKEN}"
    echo
    echo '```'
    echo
    echo 'Expected: the SE-TST finding carries `"unassessed": true` and `"severity": "UNASSESSED"`'
    echo 'and has **no** `attainment` or `shortfall` member at all, while SE-DSN carries both.'
    echo 'Asserted mechanically in `GapReportApiTests` and, at the storage tier, in'
    echo '`JpaGapReportRepositoryTests`.'
  } > "${out}"
}

capture_ownership_matrix() {
  local out="${OUT_DIR}/03-ownership-matrix.md"
  note "03 — ownership matrix -> ${out}"

  {
    echo "# Evidence 03 — ownership authorisation matrix"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo 'Every cell of the ADR-015 Amendment 2 matrix, executed against a live instance with real'
    echo 'RS256 tokens. The filter chain carries one rule — `/api/gap-reports/** authenticated()` —'
    echo 'and **no role rule at all**, because every operation here is learner-scoped and that is'
    echo 'precisely the predicate a URL pattern cannot express. Ownership is decided in'
    echo '`GapAnalysisService`, against the caller resolved through `learner.api`.'
    echo
    echo "Report under test: \`${REPORT_ID}\`, about learner \`${OWNER_LEARNER_ID}\` (\`${OWNER_USER}\`)."
    echo "Intruder: \`${INTRUDER_USER}\` — a LEARNER, so unprivileged, and not the owner."
    echo
    echo '| # | Caller | Request | Expected | Observed |'
    echo '|---|--------|---------|----------|----------|'
    printf '| 1 | %s (owner) | GET /api/gap-reports/{id} | 200 | %s |\n' "${OWNER_USER}" \
      "$(status_of "${OWNER_TOKEN}" "GET" "/api/gap-reports/${REPORT_ID}")"
    printf '| 2 | %s (unprivileged non-owner) | GET /api/gap-reports/{id} | 404 | %s |\n' \
      "${INTRUDER_USER}" "$(status_of "${INTRUDER_TOKEN}" "GET" "/api/gap-reports/${REPORT_ID}")"
    printf '| 3 | %s (privileged) | GET /api/gap-reports/{id} | 200 | %s |\n' "${ADMIN_USER}" \
      "$(status_of "${ADMIN_TOKEN}" "GET" "/api/gap-reports/${REPORT_ID}")"
    printf '| 4 | %s (owner) | GET /api/gap-reports?learnerId=self | 200 | %s |\n' "${OWNER_USER}" \
      "$(status_of "${OWNER_TOKEN}" "GET" "/api/gap-reports?learnerId=${OWNER_LEARNER_ID}")"
    printf '| 5 | %s (unprivileged non-owner) | GET /api/gap-reports?learnerId=owner | 403 | %s |\n' \
      "${INTRUDER_USER}" \
      "$(status_of "${INTRUDER_TOKEN}" "GET" "/api/gap-reports?learnerId=${OWNER_LEARNER_ID}")"
    printf '| 6 | %s (privileged) | GET /api/gap-reports?learnerId=owner | 200 | %s |\n' \
      "${ADMIN_USER}" \
      "$(status_of "${ADMIN_TOKEN}" "GET" "/api/gap-reports?learnerId=${OWNER_LEARNER_ID}")"
    printf '| 7 | (none) | GET /api/gap-reports/{id} | 401 | %s |\n' \
      "$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/api/gap-reports/${REPORT_ID}")"
    echo
    echo '**Rows 2 and 5 are the pair that matters.** Both refuse the same caller, and they differ'
    echo 'deliberately. Row 2 answers `404` because a lookup happened and `403` would confirm that'
    echo 'the identifier names a real report — which also names the learner it is about. Row 5'
    echo 'answers `403` because the learner identifier came from the caller and is never looked up'
    echo '(ADR-019), so refusing discloses nothing, and a `404` would cost a usable diagnostic for'
    echo 'no privacy gain. Row 7 is the filter chain, not the application layer.'
  } > "${out}"
}

capture_non_disclosure() {
  local out="${OUT_DIR}/04-non-disclosure.md"
  note "04 — non-disclosure -> ${out}"

  local unknown_id="00000000-0000-4000-8000-000000000000"

  {
    echo "# Evidence 04 — denial is indistinguishable from absence"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Instance: ${BASE_URL} (dev profile)"
    echo
    echo 'A caller who may not read a report receives `404`, not `403`. A `403` would confirm that'
    echo 'the identifier names a real report, turning the endpoint into an enumeration oracle — and'
    echo 'a gap report discloses which learner it is about, so the leak would be worse than for a'
    echo 'profile. The bodies below differ only in the RFC 9457 `instance` member, which echoes the'
    echo 'URI the caller itself supplied and therefore discloses nothing.'
    echo
    echo '## Existing report, caller not permitted to read it'
    echo '```http'
    curl -sS -i "${BASE_URL}/api/gap-reports/${REPORT_ID}" -H "Authorization: Bearer ${INTRUDER_TOKEN}"
    echo
    echo '```'
    echo
    echo '## Identifier that does not exist, same caller'
    echo '```http'
    curl -sS -i "${BASE_URL}/api/gap-reports/${unknown_id}" -H "Authorization: Bearer ${INTRUDER_TOKEN}"
    echo
    echo '```'
    echo
    echo 'Expected: identical status, `type`, `title` and `detail`. Asserted mechanically as a byte'
    echo 'comparison in `GapReportApiTests`, which strips `instance` and requires the remainder to'
    echo 'match exactly — a stronger check than eyeballing two transcripts, and the reason this'
    echo 'artefact is corroboration rather than the primary evidence.'
  } > "${out}"
}

capture_integration_cost() {
  local out="${OUT_DIR}/05-integration-cost.md"
  note "05 — integration cost -> ${out}"

  {
    echo "# Evidence 05 — additive integration cost across three steps"
    echo
    echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ') · Repository: $(git -C "${REPO_ROOT}" rev-parse --short HEAD)"
    echo
    echo 'The RQ2 measurement. Steps 3 and 4 demonstrated **consumer isolation**: a complete bounded'
    echo 'context added with an empty diff under the module it references. Step 5 demonstrates'
    echo 'something harder — **additive integration cost**: what it takes for an existing producer to'
    echo 'serve a genuinely new consumer. This is not a regression from zero-touch; it is the'
    echo 'question zero-touch could not answer (ADR-022).'
    echo
    echo '## Steps 3 and 4: any change under competency/src/main?'
    echo '```'
    echo "\$ git diff --stat 6c847ca..${STEP4_END} -- egas/src/main/java/ie/ul/egas/competency"
    git -C "${REPO_ROOT}" diff --stat "6c847ca..${STEP4_END}" -- egas/src/main/java/ie/ul/egas/competency
    echo "(no output above = no changes across two consecutive steps)"
    echo '```'
    echo
    echo '## Step 5: what it cost Competency Modelling'
    echo '```'
    echo "\$ git diff --numstat ${STEP4_END}..HEAD --diff-filter=M -- egas/src/main/java/ie/ul/egas/competency"
    git -C "${REPO_ROOT}" diff --numstat "${STEP4_END}..HEAD" --diff-filter=M \
      -- egas/src/main/java/ie/ul/egas/competency
    echo '```'
    echo
    echo '## Step 5: what it cost Learner Profiling'
    echo '```'
    echo "\$ git diff --numstat ${STEP4_END}..HEAD --diff-filter=M -- egas/src/main/java/ie/ul/egas/learner"
    git -C "${REPO_ROOT}" diff --numstat "${STEP4_END}..HEAD" --diff-filter=M \
      -- egas/src/main/java/ie/ul/egas/learner
    echo '```'
    echo
    echo '## Deletions or renames in either producer?'
    echo '```'
    echo "\$ git diff --numstat ${STEP4_END}..HEAD --diff-filter=DR -- <competency> <learner>"
    git -C "${REPO_ROOT}" diff --numstat "${STEP4_END}..HEAD" --diff-filter=DR \
      -- egas/src/main/java/ie/ul/egas/competency egas/src/main/java/ie/ul/egas/learner
    echo "(no output above = none)"
    echo '```'
    echo
    echo '## Module dependency declaration, unchanged by Step 5'
    echo '```java'
    grep -A2 "ApplicationModule" "${REPO_ROOT}/egas/src/main/java/ie/ul/egas/gapanalysis/package-info.java"
    echo '```'
    echo
    echo 'The DAG gained **no edge**: both dependencies were declared before Step 5 and were'
    echo 'exercised for the first time rather than newly permitted. Full analysis — including which'
    echo 'deleted line was a signature change, and why a REST DTO component is not a'
    echo 'published-contract break — is in `docs/reviews/step5-completion-review.md`.'
  } > "${out}"
}

# Artefact 05 needs no running instance; the rest do.
capture_integration_cost
require_running
setup
capture_explainability
capture_absence
capture_ownership_matrix
capture_non_disclosure

note "Done. Generated 01-05 in ${OUT_DIR}."
