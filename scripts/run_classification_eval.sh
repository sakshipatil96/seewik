#!/usr/bin/env bash
set -u

endpoint="${1:-http://127.0.0.1:8080/api/civic/classify}"
case_file="${2:-data/eval/classification-cases-v0.1.json}"
evaluation_dir="$(cd "$(dirname "$case_file")" && pwd)"
result_dir="$evaluation_dir/results"
result_file="$result_dir/classification-results-2026-08-24.ndjson"
summary_file="$result_dir/classification-summary-2026-08-24.json"

mkdir -p "$result_dir"
: > "$result_file"

total=0
correct=0
classified=0
clarification=0
errors=0
evaluated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

while IFS= read -r case_json; do
  case_id="$(jq -r '.case_id' <<<"$case_json")"
  expected="$(jq -r '.expected_issueType' <<<"$case_json")"
  input_text="$(jq -r '.input_text // ""' <<<"$case_json")"
  image_ref="$(jq -r '.image_ref // ""' <<<"$case_json")"

  if [[ -n "$image_ref" ]]; then
    image_path="$evaluation_dir/$image_ref"
    response="$(curl -sS --connect-timeout 10 --max-time 180 \
      -F "image=@$image_path;type=image/jpeg" \
      -F "text=$input_text" \
      "$endpoint" || true)"
  else
    response="$(curl -sS --connect-timeout 10 --max-time 180 \
      -F "text=$input_text" \
      "$endpoint" || true)"
  fi

  status="$(jq -r '.status // "NO_RESPONSE"' <<<"${response:-{}}" 2>/dev/null || printf 'INVALID_RESPONSE')"
  actual="$(jq -r '.issueType // ""' <<<"${response:-{}}" 2>/dev/null || true)"
  language="$(jq -r '.detectedLanguage // ""' <<<"${response:-{}}" 2>/dev/null || true)"
  confidence="$(jq -r '.confidence // "null"' <<<"${response:-{}}" 2>/dev/null || printf 'null')"
  latency_ms="$(jq -r '.latencyMs // "null"' <<<"${response:-{}}" 2>/dev/null || printf 'null')"
  model_version="$(jq -r '.modelVersion // ""' <<<"${response:-{}}" 2>/dev/null || true)"
  response_id="$(jq -r '.responseId // ""' <<<"${response:-{}}" 2>/dev/null || true)"
  description="$(jq -r '.description // .message // ""' <<<"${response:-{}}" 2>/dev/null || true)"
  error_code="$(jq -r '.errorCode // ""' <<<"${response:-{}}" 2>/dev/null || true)"

  is_correct=false
  if [[ "$actual" == "$expected" ]]; then
    is_correct=true
    correct=$((correct + 1))
  fi
  if [[ "$status" == "CLASSIFIED" ]]; then classified=$((classified + 1)); fi
  if [[ "$status" == "CLARIFICATION_REQUIRED" ]]; then clarification=$((clarification + 1)); fi
  if [[ "$status" == "CLASSIFICATION_ERROR" || "$status" == "INVALID_RESPONSE" || "$status" == "NO_RESPONSE" ]]; then
    errors=$((errors + 1))
  fi
  total=$((total + 1))

  jq -nc \
    --arg case_id "$case_id" \
    --arg expected_issueType "$expected" \
    --arg actual_issueType "$actual" \
    --arg status "$status" \
    --arg detectedLanguage "$language" \
    --argjson confidence "$confidence" \
    --argjson latencyMs "$latency_ms" \
    --arg modelVersion "$model_version" \
    --arg responseId "$response_id" \
    --arg description "$description" \
    --arg errorCode "$error_code" \
    --argjson correct "$is_correct" \
    '{case_id:$case_id,expected_issueType:$expected_issueType,actual_issueType:$actual_issueType,status:$status,correct:$correct,detectedLanguage:$detectedLanguage,confidence:$confidence,latencyMs:$latencyMs,modelVersion:$modelVersion,responseId:$responseId,description:$description,errorCode:$errorCode}' \
    >> "$result_file"
  printf '%s expected=%s actual=%s status=%s correct=%s\n' "$case_id" "$expected" "${actual:-NONE}" "$status" "$is_correct"
done < <(jq -c '.cases[]' "$case_file")

accuracy="$(awk -v correct="$correct" -v total="$total" 'BEGIN { if (total == 0) print 0; else printf "%.4f", correct / total }')"
min_latency_ms="$(jq -s 'map(.latencyMs) | min' "$result_file")"
median_latency_ms="$(jq -s 'map(.latencyMs) | sort as $values | ($values | length) as $length | if $length % 2 == 1 then $values[($length / 2) | floor] else (($values[$length / 2 - 1] + $values[$length / 2]) / 2) end' "$result_file")"
max_latency_ms="$(jq -s 'map(.latencyMs) | max' "$result_file")"
model_version="$(jq -sr 'map(.modelVersion) | map(select(length > 0)) | first // ""' "$result_file")"
jq -n \
  --arg evaluatedAt "$evaluated_at" \
  --arg endpoint "$endpoint" \
  --arg caseSetVersion "$(jq -r '.case_set_version' "$case_file")" \
  --arg schemaVersion "$(jq -r '.schema_version' "$case_file")" \
  --arg packVersion "$(jq -r '.pack_version' "$case_file")" \
  --arg modelVersion "$model_version" \
  --argjson total "$total" \
  --argjson correct "$correct" \
  --argjson accuracy "$accuracy" \
  --argjson classified "$classified" \
  --argjson clarificationRequired "$clarification" \
  --argjson errors "$errors" \
  --argjson minLatencyMs "$min_latency_ms" \
  --argjson medianLatencyMs "$median_latency_ms" \
  --argjson maxLatencyMs "$max_latency_ms" \
  '{evaluatedAt:$evaluatedAt,endpoint:$endpoint,caseSetVersion:$caseSetVersion,schemaVersion:$schemaVersion,packVersion:$packVersion,modelVersion:$modelVersion,total:$total,correct:$correct,accuracy:$accuracy,classified:$classified,clarificationRequired:$clarificationRequired,errors:$errors,latencyMs:{min:$minLatencyMs,median:$medianLatencyMs,max:$maxLatencyMs},resultsFile:"classification-results-2026-08-24.ndjson"}' \
  > "$summary_file"

jq . "$summary_file"
