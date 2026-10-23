#!/usr/bin/env bash
# Checks the API and message contracts:
#   1. lints every OpenAPI contract with Redocly and platform/api-contracts/lint-rules.yaml;
#   2. runs oasdiff against the same contract on the base ref and fails on a breaking change;
#   3. validates every AsyncAPI contract and diffs it against the base ref the same way.
# A contract that does not exist on the base ref (a new file, such as a new major version) is not
# diffed. Runs on Linux and in Git Bash on Windows; needs Node, npx and Docker.
#
# CONTRACTS_BASE_REF overrides the base ref (default: origin/main).
set -euo pipefail

REDOCLY_VERSION=2.69.0
OASDIFF_IMAGE=tufin/oasdiff:v1.34.1
ASYNCAPI_CLI_VERSION=6.2.0

cd "$(dirname "$0")/.."
ROOT="$(pwd -W 2>/dev/null || pwd)"
BASE_REF="${CONTRACTS_BASE_REF:-origin/main}"
LINT_RULES=platform/api-contracts/lint-rules.yaml

# Stop Git Bash from rewriting the container paths of `docker run -v` into Windows paths.
export MSYS_NO_PATHCONV=1

shopt -s nullglob
openapi_libraries=(platform/api-contracts/openapi/*.yaml)
openapi_services=(services/*/src/main/openapi/*.yaml platform/service-support/src/main/openapi/*.yaml)
asyncapi_libraries=(platform/api-contracts/asyncapi/*.yaml)
asyncapi_services=(services/*/src/main/asyncapi/*.yaml)

BASE_DIR="$(mktemp -d)"
trap 'rm -rf "$BASE_DIR"' EXIT
BASE_MOUNT="$(cd "$BASE_DIR" && (pwd -W 2>/dev/null || pwd))"

if ! git rev-parse --verify --quiet "$BASE_REF^{commit}" >/dev/null; then
  echo "FAIL  base ref $BASE_REF not found (fetch it, CI needs fetch-depth: 0)" >&2
  exit 1
fi

# Extract the contracts (and what they reference) of the base ref, keeping the repository layout,
# so that the relative $refs resolve on both sides of a diff.
base_files="$(git ls-tree -r --name-only "$BASE_REF" \
  | grep -E '^(platform/api-contracts/|(platform|services)/[^/]+/src/main/(openapi|asyncapi)/)' || true)"
if [ -n "$base_files" ]; then
  # Word splitting is intended: the contract paths contain no spaces.
  # shellcheck disable=SC2086
  git archive --format=tar "$BASE_REF" $base_files | tar -x -C "$BASE_DIR"
fi

exists_on_base() {
  [ -f "$BASE_DIR/$1" ]
}

failures=0
fail() {
  echo "FAIL  $1"
  failures=$((failures + 1))
}

echo "Base ref: $BASE_REF"

# 1. OpenAPI lint.
for spec in "${openapi_libraries[@]}" "${openapi_services[@]}"; do
  if npx --yes "@redocly/cli@$REDOCLY_VERSION" lint --config "$LINT_RULES" "$spec" >"$BASE_DIR/lint.log" 2>&1; then
    echo "OK    openapi lint      $spec"
  else
    cat "$BASE_DIR/lint.log"
    fail "openapi lint      $spec"
  fi
done

# 2. OpenAPI breaking changes.
for spec in "${openapi_services[@]}"; do
  if ! exists_on_base "$spec"; then
    echo "SKIP  openapi diff      $spec (not on $BASE_REF)"
  elif docker run --rm -v "$ROOT:/current:ro" -v "$BASE_MOUNT:/base:ro" "$OASDIFF_IMAGE" \
    breaking "/base/$spec" "/current/$spec" --fail-on ERR; then
    echo "OK    openapi diff      $spec"
  else
    fail "openapi diff      $spec (breaking change; a breaking change needs a new major version file)"
  fi
done

# 3. AsyncAPI validation and breaking changes.
for spec in "${asyncapi_libraries[@]}" "${asyncapi_services[@]}"; do
  if npx --yes "@asyncapi/cli@$ASYNCAPI_CLI_VERSION" validate "$spec" --fail-severity warn >/dev/null 2>&1; then
    echo "OK    asyncapi validate $spec"
  else
    npx --yes "@asyncapi/cli@$ASYNCAPI_CLI_VERSION" validate "$spec" --fail-severity warn || true
    fail "asyncapi validate $spec"
  fi
done
for spec in "${asyncapi_services[@]}"; do
  if ! exists_on_base "$spec"; then
    echo "SKIP  asyncapi diff     $spec (not on $BASE_REF)"
  elif npx --yes "@asyncapi/cli@$ASYNCAPI_CLI_VERSION" diff "$BASE_MOUNT/$spec" "$spec" --type breaking; then
    echo "OK    asyncapi diff     $spec"
  else
    fail "asyncapi diff     $spec (breaking change; a breaking change needs a new file)"
  fi
done

if [ "$failures" -gt 0 ]; then
  echo "$failures contract check(s) failed"
  exit 1
fi
echo "All contract checks passed"
