#!/usr/bin/env bash
set -euo pipefail

api_key="${FIREBASE_WEB_API_KEY:?FIREBASE_WEB_API_KEY is required}"
endpoint="${1:-https://seewik-api-528138216934.asia-south1.run.app/api/civic/classify}"
case_file="${2:-data/eval/classification-cases-v0.2.json}"
run_prefix="${3:-2026-08-26-day7}"

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
./scripts/run_classification_eval.sh "$endpoint" "$case_file" "${run_prefix}-run1"
./scripts/run_classification_eval.sh "$endpoint" "$case_file" "${run_prefix}-run2"
