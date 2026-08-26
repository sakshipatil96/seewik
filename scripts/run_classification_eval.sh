#!/usr/bin/env bash
set -u

endpoint="${1:-https://seewik-api-528138216934.asia-south1.run.app/api/civic/classify}"
case_file="${2:-data/eval/classification-cases-v0.2.json}"
run_label="${3:-$(date -u +%Y-%m-%d)-run1}"
firebase_id_token="${FIREBASE_ID_TOKEN:-}"

declared_cases_sha="$(jq -r '.cases_sha256' "$case_file")"
computed_cases_sha="$(jq -cS '.cases' "$case_file" | openssl dgst -sha256 | awk '{print $NF}')"
if [[ "$declared_cases_sha" != "$computed_cases_sha" ]]; then
  printf 'Case-set checksum mismatch: declared=%s computed=%s\n' "$declared_cases_sha" "$computed_cases_sha" >&2
  exit 3
fi

if [[ -z "$firebase_id_token" ]]; then
  printf 'FIREBASE_ID_TOKEN is required; the deployed paid endpoint rejects anonymous HTTP requests.\n' >&2
  exit 2
fi

evaluation_dir="$(cd "$(dirname "$case_file")" && pwd)"
result_dir="$evaluation_dir/results"
result_file="$result_dir/classification-results-$run_label.ndjson"
summary_file="$result_dir/classification-summary-$run_label.json"
mkdir -p "$result_dir"
: > "$result_file"

total=0
correct=0
language_correct=0
classified=0
clarification=0
model_call_errors=0
schema_errors=0
transport_errors=0
evaluated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

while IFS= read -r case_json; do
  case_id="$(jq -r '.case_id' <<<"$case_json")"
  expected="$(jq -r '.expected_issueType' <<<"$case_json")"
  expected_language="$(jq -r '.expectedLanguage' <<<"$case_json")"
  input_text="$(jq -r '.input_text // ""' <<<"$case_json")"
  image_ref="$(jq -r '.image_ref // ""' <<<"$case_json")"

  curl_exit=0
  if [[ -n "$image_ref" ]]; then
    image_path="$evaluation_dir/$image_ref"
    response="$(curl -sS --connect-timeout 10 --max-time 180 \
      -H "Authorization: Bearer $firebase_id_token" \
      -F "image=@$image_path;type=image/jpeg" \
      -F "text=$input_text" \
      "$endpoint")" || curl_exit=$?
  else
    response="$(curl -sS --connect-timeout 10 --max-time 180 \
      -H "Authorization: Bearer $firebase_id_token" \
      -F "text=$input_text" \
      "$endpoint")" || curl_exit=$?
  fi

  if ! jq -e . >/dev/null 2>&1 <<<"${response:-}"; then
    response='{"status":"INVALID_RESPONSE","errorCode":"INVALID_RESPONSE"}'
  fi
  status="$(jq -r '.status // "NO_RESPONSE"' <<<"$response")"
  actual="$(jq -r '.issueType // ""' <<<"$response")"
  language="$(jq -r '.detectedLanguage // ""' <<<"$response")"
  confidence="$(jq -r '.confidence // "null"' <<<"$response")"
  latency_ms="$(jq -r '.latencyMs // "null"' <<<"$response")"
  model_version="$(jq -r '.modelVersion // ""' <<<"$response")"
  response_id="$(jq -r '.responseId // ""' <<<"$response")"
  error_code="$(jq -r '.errorCode // ""' <<<"$response")"

  is_correct=false
  if [[ "$actual" == "$expected" ]]; then is_correct=true; correct=$((correct + 1)); fi
  is_language_correct=false
  if [[ "$language" == "$expected_language" ]]; then is_language_correct=true; language_correct=$((language_correct + 1)); fi
  if [[ "$status" == "CLASSIFIED" ]]; then classified=$((classified + 1)); fi
  if [[ "$status" == "CLARIFICATION_REQUIRED" ]]; then clarification=$((clarification + 1)); fi
  if [[ "$error_code" == "MODEL_CALL_FAILED" ]]; then model_call_errors=$((model_call_errors + 1)); fi
  if [[ "$error_code" == "SCHEMA_VALIDATION_FAILED" ]]; then schema_errors=$((schema_errors + 1)); fi
  if [[ "$curl_exit" -ne 0 || "$status" == "INVALID_RESPONSE" || "$status" == "NO_RESPONSE" ]]; then transport_errors=$((transport_errors + 1)); fi
  total=$((total + 1))

  jq -nc \
    --arg case_id "$case_id" \
    --arg expectedIssueType "$expected" \
    --arg actualIssueType "$actual" \
    --arg expectedLanguage "$expected_language" \
    --arg detectedLanguage "$language" \
    --arg status "$status" \
    --argjson confidence "$confidence" \
    --argjson latencyMs "$latency_ms" \
    --arg modelVersion "$model_version" \
    --arg responseId "$response_id" \
    --arg errorCode "$error_code" \
    --argjson correct "$is_correct" \
    --argjson languageCorrect "$is_language_correct" \
    --argjson curlExit "$curl_exit" \
    --argjson rawResponse "$response" \
    '{case_id:$case_id,expectedIssueType:$expectedIssueType,actualIssueType:$actualIssueType,correct:$correct,expectedLanguage:$expectedLanguage,detectedLanguage:$detectedLanguage,languageCorrect:$languageCorrect,status:$status,confidence:$confidence,latencyMs:$latencyMs,modelVersion:$modelVersion,responseId:$responseId,errorCode:$errorCode,curlExit:$curlExit,rawResponse:$rawResponse}' \
    >> "$result_file"
  printf '%s expected=%s actual=%s language=%s status=%s correct=%s\n' "$case_id" "$expected" "${actual:-NONE}" "${language:-NONE}" "$status" "$is_correct"
done < <(jq -c '.cases[]' "$case_file")

accuracy="$(awk -v correct="$correct" -v total="$total" 'BEGIN { if (total == 0) print 0; else printf "%.4f", correct / total }')"
language_accuracy="$(awk -v correct="$language_correct" -v total="$total" 'BEGIN { if (total == 0) print 0; else printf "%.4f", correct / total }')"
latencies="$(jq -sc 'map(.latencyMs) | map(select(type == "number")) | sort' "$result_file")"
min_latency_ms="$(jq 'if length == 0 then null else min end' <<<"$latencies")"
median_latency_ms="$(jq 'if length == 0 then null else . as $v | length as $n | if $n % 2 == 1 then $v[($n/2)|floor] else (($v[$n/2-1]+$v[$n/2])/2) end end' <<<"$latencies")"
p95_latency_ms="$(jq 'if length == 0 then null else .[((length * 0.95 | ceil) - 1)] end' <<<"$latencies")"
max_latency_ms="$(jq 'if length == 0 then null else max end' <<<"$latencies")"
model_version="$(jq -sr 'map(.modelVersion) | map(select(length > 0)) | first // ""' "$result_file")"
git_sha="$(git rev-parse HEAD 2>/dev/null || printf 'UNCOMMITTED')"

jq -n \
  --arg evaluatedAt "$evaluated_at" \
  --arg endpoint "$endpoint" \
  --arg region "asia-south1" \
  --arg runLabel "$run_label" \
  --arg gitSha "$git_sha" \
  --arg caseSetVersion "$(jq -r '.case_set_version' "$case_file")" \
  --arg casesSha256 "$(jq -r '.cases_sha256' "$case_file")" \
  --arg promptVersion "$(jq -r '.prompt_version' "$case_file")" \
  --arg schemaVersion "$(jq -r '.schema_version' "$case_file")" \
  --arg packVersion "$(jq -r '.pack_version' "$case_file")" \
  --arg modelVersion "$model_version" \
  --arg resultsFile "$(basename "$result_file")" \
  --argjson total "$total" \
  --argjson correct "$correct" \
  --argjson accuracy "$accuracy" \
  --argjson languageCorrect "$language_correct" \
  --argjson languageAccuracy "$language_accuracy" \
  --argjson classified "$classified" \
  --argjson clarificationRequired "$clarification" \
  --argjson modelCallErrors "$model_call_errors" \
  --argjson schemaValidationErrors "$schema_errors" \
  --argjson transportErrors "$transport_errors" \
  --argjson minLatencyMs "$min_latency_ms" \
  --argjson medianLatencyMs "$median_latency_ms" \
  --argjson p95LatencyMs "$p95_latency_ms" \
  --argjson maxLatencyMs "$max_latency_ms" \
  '{evaluatedAt:$evaluatedAt,endpoint:$endpoint,region:$region,runLabel:$runLabel,gitSha:$gitSha,caseSetVersion:$caseSetVersion,casesSha256:$casesSha256,promptVersion:$promptVersion,schemaVersion:$schemaVersion,packVersion:$packVersion,modelVersion:$modelVersion,total:$total,correct:$correct,accuracy:$accuracy,languageCorrect:$languageCorrect,languageAccuracy:$languageAccuracy,classified:$classified,clarificationRequired:$clarificationRequired,failures:{modelCall:$modelCallErrors,schemaValidation:$schemaValidationErrors,transport:$transportErrors},latencyMs:{min:$minLatencyMs,median:$medianLatencyMs,p95:$p95LatencyMs,max:$maxLatencyMs},resultsFile:$resultsFile}' \
  > "$summary_file"

jq . "$summary_file"
