#!/usr/bin/env bash
# Documentation-reference integrity check.
#
# Three times during Steps 3-4 an Accepted ADR cited an artefact that did not exist: a module
# diagram, a database table, and a test class. Each was caught by human review rather than by any
# mechanism — an uncomfortable gap in a project whose central claim is that its architecture is
# mechanically enforced. This script closes the part of that gap which can be closed cheaply.
#
#   ./docs/check-doc-references.sh          # exits non-zero on a dangling reference
#
# SCOPE, deliberately narrow. It resolves two kinds of backticked token in docs/*.md:
#
#   1. Repository paths      — anything containing '/' and a file extension
#   2. Project test classes  — identifiers ending in 'Tests'
#
# It does NOT attempt to resolve every backticked identifier. Prose is full of third-party types,
# SQL identifiers, claim names and method fragments, and a checker that guessed at those would
# produce false alarms until it was ignored — which is worse than no checker. Paths and test
# classes are unambiguous, cover two of the three historical defects exactly, and cost nothing.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

DOCS=$(find docs -name '*.md' -not -path '*/evidence/*' | sort)
ALLOWLIST="docs/.doc-reference-allowlist"
failures=0
checked=0
skipped=0

# A token is allowed only if a human wrote it into the allowlist with a stated reason. Correction
# notes legitimately name artefacts that no longer exist — that is what makes them corrections —
# and frozen historical documents are not edited. Keeping those explicit, and counting them aloud,
# is what stops the allowlist becoming a place to hide a genuine defect.
allowed() {
  [ -f "${ALLOWLIST}" ] && grep -qxF "$1" <(grep -v '^\s*#' "${ALLOWLIST}" | grep -v '^\s*$')
}

# Docs cite paths relative to varying roots, so resolve by suffix rather than demanding an exact
# match from the repository root.
resolves_as_path() {
  [ -e "$1" ] && return 0
  find . -path "*/$1" -not -path './.git/*' -print -quit 2>/dev/null | grep -q .
}

report() {
  printf '  MISSING  %-52s referenced by %s\n' "$1" "$2"
  failures=$((failures + 1))
}

for doc in ${DOCS}; do
  # --- 1. repository paths -------------------------------------------------------------------
  # Backticked tokens containing a slash and ending in a known extension.
  while IFS= read -r path; do
    [ -z "${path}" ] && continue
    if allowed "${path}"; then skipped=$((skipped + 1)); continue; fi
    checked=$((checked + 1))
    resolves_as_path "${path}" || report "${path}" "${doc}"
  done < <(grep -o '`[A-Za-z0-9_./-]\+`' "${doc}" \
             | tr -d '`' \
             | grep -E '/.*\.(md|puml|java|sql|yml|yaml|xml|properties|sh|png)$' \
             | sort -u)

  # --- 2. project test classes ---------------------------------------------------------------
  # Identifiers ending in 'Tests' are unambiguously ours; no dependency ships such a type.
  while IFS= read -r cls; do
    [ -z "${cls}" ] && continue
    if allowed "${cls}"; then skipped=$((skipped + 1)); continue; fi
    checked=$((checked + 1))
    find egas/src/test -name "${cls}.java" | grep -q . || report "${cls}" "${doc}"
  done < <(grep -o '`[A-Z][A-Za-z0-9]*Tests`' "${doc}" \
             | tr -d '`' \
             | sort -u)
done

echo
if [ "${failures}" -eq 0 ]; then
  echo "documentation references OK — ${checked} resolved across $(echo "${DOCS}" | wc -w) files" \
       "(${skipped} allowlisted)"
  exit 0
fi

echo "documentation references FAILED — ${failures} dangling of ${checked} checked"
exit 1
