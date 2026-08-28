#!/usr/bin/env bash
set -u

endpoint="${1:-https://seewik-api-528138216934.asia-south1.run.app/api/civic/classify}"
case_file="${2:-data/eval/classification-image-cases-v0.1-draft.json}"
private_image_dir="${3:?private sanitized image directory is required}"
run_label="${4:-$(date -u +%Y-%m-%d)-image-run1}"
firebase_id_token="${FIREBASE_ID_TOKEN:-}"
private_raw_dir="${TRACK_B_PRIVATE_RESULTS_DIR:?TRACK_B_PRIVATE_RESULTS_DIR is required}"
run_mode="${TRACK_B_RUN_MODE:-SCORED}"

if [[ "$run_mode" != "SCORED" && "$run_mode" != "DIAGNOSTIC_UNSCORED" ]]; then
  printf 'TRACK_B_RUN_MODE must be SCORED or DIAGNOSTIC_UNSCORED.\n' >&2
  exit 2
fi

if [[ -z "$firebase_id_token" ]]; then
  printf 'FIREBASE_ID_TOKEN is required; the deployed paid endpoint rejects anonymous HTTP requests.\n' >&2
  exit 2
fi

declared_cases_sha="$(jq -r '.cases_sha256' "$case_file")"
canonical_cases="$(jq -cS '.cases' "$case_file")"
computed_cases_sha="$(printf '%s' "$canonical_cases" | openssl dgst -sha256 | awk '{print $NF}')"
if [[ "$declared_cases_sha" != "$computed_cases_sha" ]]; then
  printf 'Case-set checksum mismatch: declared=%s computed=%s\n' "$declared_cases_sha" "$computed_cases_sha" >&2
  exit 3
fi
if [[ "$(jq -r '.status' "$case_file")" != "FROZEN_BEFORE_SCORED_RUN" ]]; then
  printf 'Image case set must be frozen before a scored run.\n' >&2
  exit 3
fi
if [[ "$(jq -r '.input_contract.image_only' "$case_file")" != "true" || "$(jq -r '.input_contract.text_hints' "$case_file")" != "false" ]]; then
  printf 'Image evaluation must be image-only with no text hints.\n' >&2
  exit 3
fi

result_dir="$(cd "$(dirname "$case_file")" && pwd)/results"
result_file="$result_dir/classification-image-results-$run_label.ndjson"
summary_file="$result_dir/classification-image-summary-$run_label.json"
private_raw_file="$private_raw_dir/classification-image-raw-$run_label.ndjson"
mkdir -p "$result_dir" "$private_raw_dir"
: > "$result_file"
: > "$private_raw_file"

while IFS= read -r case_json; do
  case_id="$(jq -r '.case_id' <<<"$case_json")"
  image_ref="$(jq -r '.private_image_ref' <<<"$case_json")"
  expected="$(jq -r '.expected_issueType' <<<"$case_json")"
  expected_sha="$(jq -r '.sanitized_image_sha256' <<<"$case_json")"
  expected_bytes="$(jq -r '.sanitized_bytes' <<<"$case_json")"
  mime_type="$(jq -r '.mime_type' <<<"$case_json")"
  case_scored="$(jq -r '.scored' <<<"$case_json")"
  scored="$case_scored"
  if [[ "$run_mode" == "DIAGNOSTIC_UNSCORED" ]]; then scored=false; fi
  image_path="$private_image_dir/$image_ref"

  if [[ ! -f "$image_path" ]]; then
    printf '%s is missing from the private sanitized image directory.\n' "$case_id" >&2
    exit 4
  fi
  actual_sha="$(openssl dgst -sha256 "$image_path" | awk '{print $NF}')"
  actual_bytes="$(stat -f '%z' "$image_path" 2>/dev/null || stat -c '%s' "$image_path")"
  if [[ "$actual_sha" != "$expected_sha" || "$actual_bytes" != "$expected_bytes" ]]; then
    printf '%s image integrity mismatch; scored calls were not started.\n' "$case_id" >&2
    exit 4
  fi

  curl_exit=0
  curl_payload="$(curl -sS --connect-timeout 10 --max-time 180 \
    -H "Authorization: Bearer $firebase_id_token" \
    -F "image=@$image_path;type=$mime_type" \
    -w $'\n__HTTP_STATUS__:%{http_code}' \
    "$endpoint")" || curl_exit=$?

  http_status="${curl_payload##*$'\n__HTTP_STATUS__:'}"
  raw_response="${curl_payload%$'\n__HTTP_STATUS__:'*}"
  if [[ "$http_status" == "$curl_payload" || ! "$http_status" =~ ^[0-9]{3}$ ]]; then http_status=0; fi
  response_is_json=true
  if ! jq -e . >/dev/null 2>&1 <<<"${raw_response:-}"; then
    response_is_json=false
    response='{"status":"INVALID_RESPONSE","errorCode":"INVALID_RESPONSE"}'
  else
    response="$raw_response"
  fi

  status="$(jq -r '.status // "NO_RESPONSE"' <<<"$response")"
  actual="$(jq -r '.issueType // ""' <<<"$response")"
  confidence="$(jq -r '.confidence // "null"' <<<"$response")"
  latency_ms="$(jq -r '.latencyMs // "null"' <<<"$response")"
  model_version="$(jq -r '.modelVersion // ""' <<<"$response")"
  detected_language="$(jq -r '.detectedLanguage // ""' <<<"$response")"
  needs_clarification="$(jq -r '.needsClarification // false' <<<"$response")"
  error_code="$(jq -r '.errorCode // ""' <<<"$response")"
  validator_subcode="$(jq -r '.validatorSubcode // ""' <<<"$response")"
  generated_output_length="$(jq -r '.generatedOutputLength // "null"' <<<"$response")"
  finish_reason="$(jq -r '.finishReason // ""' <<<"$response")"
  candidates_token_count="$(jq -r '.candidatesTokenCount // "null"' <<<"$response")"
  response_sha="$(printf '%s' "$raw_response" | openssl dgst -sha256 | awk '{print $NF}')"

  failure_class="NONE"
  if [[ "$curl_exit" -eq 28 || "$error_code" == "MODEL_TIMEOUT" ]]; then
    failure_class="TIMEOUT"
  elif [[ "$error_code" == "MODEL_CALL_FAILED" ]]; then
    failure_class="MODEL"
  elif [[ "$error_code" == "SCHEMA_VALIDATION_FAILED" ]]; then
    failure_class="SCHEMA"
  elif [[ "$curl_exit" -ne 0 || "$status" == "INVALID_RESPONSE" || "$status" == "NO_RESPONSE" ]]; then
    failure_class="TRANSPORT"
  elif [[ "$http_status" -lt 200 || "$http_status" -ge 300 ]]; then
    failure_class="HTTP"
  fi

  label_match=false
  if [[ "$failure_class" == "NONE" && "$actual" == "$expected" ]]; then label_match=true; fi
  correct=false
  if [[ "$scored" == "true" && "$label_match" == "true" ]]; then correct=true; fi

  jq -nc \
    --arg case_id "$case_id" \
    --arg runMode "$run_mode" \
    --arg imageSha256 "$actual_sha" \
    --arg expectedIssueType "$expected" \
    --argjson scored "$scored" \
    --argjson curlExit "$curl_exit" \
    --argjson httpStatus "$http_status" \
    --arg rawResponseText "$raw_response" \
    --argjson rawResponse "$([[ "$response_is_json" == true ]] && printf '%s' "$raw_response" || printf 'null')" \
    '{case_id:$case_id,runMode:$runMode,imageSha256:$imageSha256,expectedIssueType:$expectedIssueType,scored:$scored,curlExit:$curlExit,httpStatus:$httpStatus,rawResponseText:$rawResponseText,rawResponse:$rawResponse}' \
    >> "$private_raw_file"

  jq -nc \
    --arg case_id "$case_id" \
    --arg runMode "$run_mode" \
    --arg expectedIssueType "$expected" \
    --arg actualIssueType "$actual" \
    --arg status "$status" \
    --argjson confidence "$confidence" \
    --arg detectedLanguage "$detected_language" \
    --argjson needsClarification "$needs_clarification" \
    --argjson latencyMs "$latency_ms" \
    --arg modelVersion "$model_version" \
    --arg errorCode "$error_code" \
    --arg validatorSubcode "$validator_subcode" \
    --argjson generatedOutputLength "$generated_output_length" \
    --arg finishReason "$finish_reason" \
    --argjson candidatesTokenCount "$candidates_token_count" \
    --arg failureClass "$failure_class" \
    --arg responseSha256 "$response_sha" \
    --argjson correct "$correct" \
    --argjson labelMatch "$label_match" \
    --argjson scored "$scored" \
    --argjson curlExit "$curl_exit" \
    --argjson httpStatus "$http_status" \
    '{case_id:$case_id,runMode:$runMode,expectedIssueType:$expectedIssueType,actualIssueType:$actualIssueType,labelMatch:$labelMatch,correct:$correct,scored:$scored,status:$status,confidence:$confidence,detectedLanguage:$detectedLanguage,needsClarification:$needsClarification,latencyMs:$latencyMs,modelVersion:$modelVersion,errorCode:$errorCode,validatorSubcode:$validatorSubcode,generatedOutputLength:$generatedOutputLength,finishReason:$finishReason,candidatesTokenCount:$candidatesTokenCount,failureClass:$failureClass,curlExit:$curlExit,httpStatus:$httpStatus,responseSha256:$responseSha256}' \
    >> "$result_file"

  printf '%s expected=%s actual=%s status=%s labelMatch=%s scored=%s failure=%s validator=%s finish=%s\n' "$case_id" "$expected" "${actual:-NONE}" "$status" "$label_match" "$scored" "$failure_class" "${validator_subcode:-NONE}" "${finish_reason:-NONE}"
  sleep 2
done < <(jq -c '.cases[]' "$case_file")

evaluated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git rev-parse HEAD 2>/dev/null || printf 'UNCOMMITTED')"
metrics="$(jq -sc '
  def latency($v): ($v | map(select(type == "number")) | sort) as $x
    | if ($x|length) == 0 then {min:null,median:null,p95:null,max:null}
      else {min:($x|min),median:(if ($x|length)%2 == 1 then $x[((($x|length)/2)|floor)] else (($x[($x|length)/2-1]+$x[($x|length)/2])/2) end),p95:$x[(((($x|length)*0.95)|ceil)-1)],max:($x|max)} end;
  . as $rows
  | ($rows | map(select(.scored))) as $scored
  | ($rows | map(select(.failureClass=="NONE"))) as $valid
  | {
      total:($rows|length),
      scored:($scored|length),
      correct:($scored|map(select(.correct))|length),
      accuracy:(if ($scored|length)==0 then null else (($scored|map(select(.correct))|length)/($scored|length)) end),
      validResponses:($valid|length),
      schemaValidity:(if ($rows|length)==0 then null else (($valid|length)/($rows|length)) end),
      categoryCorrectOnValidResponses:($valid|map(select(.labelMatch))|length),
      categoryAccuracyOnValidResponses:(if ($valid|length)==0 then null else (($valid|map(select(.labelMatch))|length)/($valid|length)) end),
      misclassifications:($valid|map(select(.labelMatch==false))|length),
      classified:($rows|map(select(.status=="CLASSIFIED"))|length),
      clarificationRequired:($rows|map(select(.needsClarification==true or .status=="CLARIFICATION_REQUIRED"))|length),
      failures:{
        none:($rows|map(select(.failureClass=="NONE"))|length),
        timeout:($rows|map(select(.failureClass=="TIMEOUT"))|length),
        model:($rows|map(select(.failureClass=="MODEL"))|length),
        schema:($rows|map(select(.failureClass=="SCHEMA"))|length),
        transport:($rows|map(select(.failureClass=="TRANSPORT"))|length),
        http:($rows|map(select(.failureClass=="HTTP"))|length)
      },
      latencyMs:latency($rows|map(.latencyMs)),
      confusionPairs:($scored|map(select(.correct==false and .actualIssueType!=""))|group_by([.expectedIssueType,.actualIssueType])|map({expectedIssueType:.[0].expectedIssueType,actualIssueType:.[0].actualIssueType,count:length})),
      perCategory:($scored|group_by(.expectedIssueType)|map({issueType:.[0].expectedIssueType,cases:length,correct:(map(select(.correct))|length),accuracy:((map(select(.correct))|length)/length)})),
      perCategoryValid:($valid|group_by(.expectedIssueType)|map({issueType:.[0].expectedIssueType,responses:length,correct:(map(select(.labelMatch))|length),accuracy:((map(select(.labelMatch))|length)/length)})),
      schemaFailureDiagnostics:($rows|map(select(.failureClass=="SCHEMA")|{caseId:.case_id,validatorSubcode,generatedOutputLength,finishReason,candidatesTokenCount}))
    }
' "$result_file")"

jq -n \
  --arg evaluatedAt "$evaluated_at" \
  --arg endpoint "$endpoint" \
  --arg region "asia-south1" \
  --arg runLabel "$run_label" \
  --arg runMode "$run_mode" \
  --arg gitSha "$git_sha" \
  --arg caseSetVersion "$(jq -r '.case_set_version' "$case_file")" \
  --arg casesSha256 "$declared_cases_sha" \
  --arg promptVersion "$(jq -r '.prompt_version' "$case_file")" \
  --arg schemaVersion "$(jq -r '.schema_version' "$case_file")" \
  --arg packVersion "$(jq -r '.pack_version' "$case_file")" \
  --arg scoringVersion "$(jq -r '.scoring_version' "$case_file")" \
  --arg frozenModelVersion "$(jq -r '.model_version' "$case_file")" \
  --arg resultsFile "$(basename "$result_file")" \
  --argjson metrics "$metrics" \
  '{evaluatedAt:$evaluatedAt,endpoint:$endpoint,region:$region,runLabel:$runLabel,runMode:$runMode,gitSha:$gitSha,caseSetVersion:$caseSetVersion,casesSha256:$casesSha256,promptVersion:$promptVersion,schemaVersion:$schemaVersion,packVersion:$packVersion,scoringVersion:$scoringVersion,frozenModelVersion:$frozenModelVersion,inputMode:"IMAGE_ONLY_NO_TEXT_HINTS",silentRetries:false,rawResponsesPreservedPrivately:true,resultsFile:$resultsFile} + $metrics' \
  > "$summary_file"

jq . "$summary_file"
