#!/usr/bin/env bash
# Checks the API and message contracts:
#   0. self-tests of the gates: the classifier unit tests and the negative lint fixture, which must
#      trip every rule of platform/api-contracts/lint-rules.yaml;
#   1. lints every OpenAPI contract with Redocly and platform/api-contracts/lint-rules.yaml;
#   2. runs oasdiff against the same contract on the base ref and fails on a breaking change;
#   3. validates every AsyncAPI contract and diffs it against the base ref the same way.
# A contract that does not exist on the base ref (a new file, such as a new major version) is not
# diffed. Runs on Linux and in Git Bash on Windows; needs Node, npx and Docker.
#
# A failure is labelled "breaking change" when a contract broke the rules and "tool error" when a
# tool could not run or produced no usable result; the output of the tool is printed either way.
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
LINT_FIXTURE=scripts/lint-fixtures/bad.v1.yaml

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
  | grep -E '^(platform/api-contracts/|(platform|services)/[^/]+/src/main/(openapi|asyncapi)/|platform/contracts/src/main/resources/schemas/)' || true)"
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

# Runs a command, keeping its output in $output and its exit code in $status.
run() {
  status=0
  output="$("$@" 2>&1)" || status=$?
}

redocly() {
  npx --yes "@redocly/cli@$REDOCLY_VERSION" "$@"
}

asyncapi_cli() {
  npx --yes "@asyncapi/cli@$ASYNCAPI_CLI_VERSION" "$@"
}

echo "Base ref: $BASE_REF"

# 0. Self-tests of the gates.
run node --test scripts/classify-asyncapi-diff.test.mjs
if [ "$status" -eq 0 ]; then
  echo "OK    self-test         asyncapi diff classifier"
else
  echo "$output"
  fail "self-test         asyncapi diff classifier"
fi

# The negative fixture must fail the lint, and every rule of the style guide must show up in the
# output, so a rule that stops firing (for example a broken `where` clause) is noticed.
run redocly lint --config "$LINT_RULES" "$LINT_FIXTURE"
if [ "$status" -eq 0 ]; then
  echo "$output"
  fail "self-test         lint fixture $LINT_FIXTURE passed the lint"
else
  missing=""
  for rule in $(grep -oE '^  rule/[a-z0-9-]+' "$LINT_RULES" | tr -d ' '); do
    if ! grep -q -- "$rule" <<<"$output"; then
      missing="$missing $rule"
    fi
  done
  if [ -z "$missing" ]; then
    echo "OK    self-test         lint fixture trips every rule"
  else
    echo "$output"
    fail "self-test         lint fixture does not trip:$missing"
  fi
fi

# 1. OpenAPI lint.
for spec in "${openapi_libraries[@]}" "${openapi_services[@]}"; do
  run redocly lint --config "$LINT_RULES" "$spec"
  if [ "$status" -eq 0 ]; then
    echo "OK    openapi lint      $spec"
  else
    echo "$output"
    fail "openapi lint      $spec"
  fi
done

# 2. OpenAPI breaking changes. oasdiff exits non-zero both for a breaking change and for a failure
# to load a spec; the breaking changes are the lines that start with "error" or "warning" and a
# rule id in brackets. Docker failing to run the image is a tool error.
for spec in "${openapi_services[@]}"; do
  if ! exists_on_base "$spec"; then
    echo "SKIP  openapi diff      $spec (not on $BASE_REF)"
    continue
  fi
  run docker run --rm -v "$ROOT:/current:ro" -v "$BASE_MOUNT:/base:ro" "$OASDIFF_IMAGE" \
    breaking "/base/$spec" "/current/$spec" --fail-on ERR
  if [ "$status" -eq 0 ]; then
    echo "OK    openapi diff      $spec"
  else
    echo "$output"
    if grep -qE '^(error|warning)[[:space:]]+\[' <<<"$output"; then
      fail "openapi diff      $spec (breaking change; a breaking change needs a new major version file)"
    else
      fail "openapi diff      $spec (tool error: oasdiff or docker failed, exit $status)"
    fi
  fi
done

# 3. AsyncAPI validation, once per document.
for spec in "${asyncapi_libraries[@]}" "${asyncapi_services[@]}"; do
  run asyncapi_cli validate "$spec" --fail-severity warn
  if [ "$status" -eq 0 ]; then
    echo "OK    asyncapi validate $spec"
  else
    echo "$output"
    fail "asyncapi validate $spec"
  fi
done

# AsyncAPI breaking changes. The payload schemas are external files, so both sides are bundled
# (external $refs inlined) before the diff; scripts/classify-asyncapi-diff.mjs then decides which
# changes are breaking.
for spec in "${asyncapi_services[@]}"; do
  if ! exists_on_base "$spec"; then
    echo "SKIP  asyncapi diff     $spec (not on $BASE_REF)"
    continue
  fi
  dir="$BASE_MOUNT/bundled/$spec"
  mkdir -p "$BASE_DIR/bundled/$spec"
  run asyncapi_cli bundle "$BASE_MOUNT/$spec" --output "$dir/base.yaml"
  if [ "$status" -ne 0 ]; then
    echo "$output"
    fail "asyncapi diff     $spec (tool error: bundling the base version failed, exit $status)"
    continue
  fi
  run asyncapi_cli bundle "$spec" --output "$dir/current.yaml"
  if [ "$status" -ne 0 ]; then
    echo "$output"
    fail "asyncapi diff     $spec (tool error: bundling the current version failed, exit $status)"
    continue
  fi
  run asyncapi_cli diff "$dir/base.yaml" "$dir/current.yaml" --type all --format json --no-error \
    --save-output "$dir/diff.json"
  if [ "$status" -ne 0 ] || [ ! -f "$BASE_DIR/bundled/$spec/diff.json" ]; then
    echo "$output"
    fail "asyncapi diff     $spec (tool error: asyncapi diff failed, exit $status)"
    continue
  fi
  run node scripts/classify-asyncapi-diff.mjs "$dir/diff.json"
  if [ "$status" -eq 0 ]; then
    echo "OK    asyncapi diff     $spec"
  elif grep -q 'unexpected output' <<<"$output"; then
    echo "$output"
    fail "asyncapi diff     $spec (tool error: unreadable diff output)"
  else
    echo "$output"
    fail "asyncapi diff     $spec (breaking change; a breaking change needs a new file)"
  fi
done

if [ "$failures" -gt 0 ]; then
  echo "$failures contract check(s) failed"
  exit 1
fi
echo "All contract checks passed"
