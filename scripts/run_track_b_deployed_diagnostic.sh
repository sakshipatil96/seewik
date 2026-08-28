#!/usr/bin/env bash
set -euo pipefail

api_key="${FIREBASE_WEB_API_KEY:?FIREBASE_WEB_API_KEY is required}"
endpoint="${1:-https://seewik-api-528138216934.asia-south1.run.app/api/civic/classify}"
case_file="${2:-data/eval/classification-image-cases-v0.1-draft.json}"
private_image_dir="${3:?private sanitized image directory is required}"
run_label="${4:-$(date -u +%Y-%m-%d)-day9-set5-diagnostic1}"
private_results_dir="${5:?private raw response directory is required}"

signup_response="$(curl -sS \
  -H 'Content-Type: application/json' \
  -d '{"returnSecureToken":true}' \
  "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${api_key}")"
firebase_id_token="$(jq -r '.idToken // empty' <<<"$signup_response")"
firebase_uid="$(jq -r '.localId // empty' <<<"$signup_response")"

if [[ -z "$firebase_id_token" || -z "$firebase_uid" ]]; then
  printf 'Temporary diagnostic user could not be created.\n' >&2
  exit 2
fi

cleanup() {
  local delete_response
  delete_response="$(curl -sS \
    -H 'Content-Type: application/json' \
    -d "{\"idToken\":\"${firebase_id_token}\"}" \
    "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${api_key}" || true)"
  if [[ "$(jq -r '.kind // empty' <<<"$delete_response")" == "identitytoolkit#DeleteAccountResponse" ]]; then
    printf 'Temporary diagnostic user deleted.\n'
  else
    printf 'Temporary diagnostic user cleanup could not be confirmed.\n' >&2
  fi
}
trap cleanup EXIT

export FIREBASE_ID_TOKEN="$firebase_id_token"
export TRACK_B_PRIVATE_RESULTS_DIR="$private_results_dir"
export TRACK_B_RUN_MODE="DIAGNOSTIC_UNSCORED"
./scripts/run_image_classification_eval.sh "$endpoint" "$case_file" "$private_image_dir" "$run_label"
