#!/usr/bin/env bash
set -euo pipefail

api_key="${FIREBASE_WEB_API_KEY:?FIREBASE_WEB_API_KEY is required}"
endpoint="${1:-https://seewik-api-528138216934.asia-south1.run.app/api/civic/classify}"
case_file="${2:-data/eval/classification-image-cases-v0.1-draft.json}"
private_image_dir="${3:?private sanitized image directory is required}"
run_prefix="${4:-$(date -u +%Y-%m-%d)-day9-set5}"
private_results_dir="${5:?private raw response directory is required}"

signup_response="$(curl -sS \
  -H 'Content-Type: application/json' \
  -d '{"returnSecureToken":true}' \
  "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${api_key}")"
firebase_id_token="$(jq -r '.idToken // empty' <<<"$signup_response")"
firebase_uid="$(jq -r '.localId // empty' <<<"$signup_response")"

if [[ -z "$firebase_id_token" || -z "$firebase_uid" ]]; then
  printf 'Temporary evaluation user could not be created.\n' >&2
  exit 2
fi

cleanup() {
  local delete_response
  delete_response="$(curl -sS \
    -H 'Content-Type: application/json' \
    -d "{\"idToken\":\"${firebase_id_token}\"}" \
    "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${api_key}" || true)"
  if [[ "$(jq -r '.kind // empty' <<<"$delete_response")" == "identitytoolkit#DeleteAccountResponse" ]]; then
    printf 'Temporary evaluation user deleted.\n'
  else
    printf 'Temporary evaluation user cleanup could not be confirmed.\n' >&2
  fi
}
trap cleanup EXIT

export FIREBASE_ID_TOKEN="$firebase_id_token"
export TRACK_B_PRIVATE_RESULTS_DIR="$private_results_dir"
export TRACK_B_RUN_MODE="SCORED"
./scripts/run_image_classification_eval.sh "$endpoint" "$case_file" "$private_image_dir" "${run_prefix}-run1"
./scripts/run_image_classification_eval.sh "$endpoint" "$case_file" "$private_image_dir" "${run_prefix}-run2"

result_dir="$(cd "$(dirname "$case_file")" && pwd)/results"
./scripts/summarize_image_classification_eval.sh \
  "$result_dir/classification-image-results-${run_prefix}-run1.ndjson" \
  "$result_dir/classification-image-results-${run_prefix}-run2.ndjson" \
  "$result_dir/classification-image-repeatability-summary-${run_prefix}.json" \
  "$result_dir/classification-image-summary-${run_prefix}-run1.json" \
  "$result_dir/classification-image-summary-${run_prefix}-run2.json"
