#!/usr/bin/env bash
set -euo pipefail

run1_file="${1:?run 1 result file is required}"
run2_file="${2:?run 2 result file is required}"
output_file="${3:?repeatability summary output file is required}"
run1_summary="${4:?run 1 summary file is required}"
run2_summary="${5:?run 2 summary file is required}"

run1_sha="$(jq -r '.casesSha256' "$run1_summary")"
run2_sha="$(jq -r '.casesSha256' "$run2_summary")"
if [[ "$run1_sha" != "$run2_sha" ]]; then
  printf 'The two runs used different frozen case sets.\n' >&2
  exit 3
fi

jq -n \
  --slurpfile run1 "$run1_file" \
  --slurpfile run2 "$run2_file" \
  --slurpfile summary1 "$run1_summary" \
  --slurpfile summary2 "$run2_summary" \
  --arg run1File "$run1_file" \
  --arg run2File "$run2_file" \
  --arg run1Summary "$run1_summary" \
  --arg run2Summary "$run2_summary" '
  def latency($v): ($v | map(select(type == "number")) | sort) as $x
    | if ($x|length) == 0 then {min:null,median:null,p95:null,max:null}
      else {min:($x|min),median:(if ($x|length)%2 == 1 then $x[((($x|length)/2)|floor)] else (($x[($x|length)/2-1]+$x[($x|length)/2])/2) end),p95:$x[(((($x|length)*0.95)|ceil)-1)],max:($x|max)} end;
  ($run1 + $run2) as $all
  | ($all | map(select(.scored))) as $scored
  | ($all | map(select(.failureClass=="NONE"))) as $valid
  | ([$run1[] | select(.failureClass=="SCHEMA") | {run:"run1",caseId:.case_id}]
      + [$run2[] | select(.failureClass=="SCHEMA") | {run:"run2",caseId:.case_id}]) as $schemaFailures
  | [range(0; $run1|length) | {
      case_id:$run1[.].case_id,
      categoryStable:($run1[.].actualIssueType==$run2[.].actualIssueType),
      statusStable:($run1[.].status==$run2[.].status),
      clarificationStable:($run1[.].needsClarification==$run2[.].needsClarification)
    }] as $pairs
  | {
      evaluatedAt:$summary2[0].evaluatedAt,
      analyzedAt:now|todate,
      endpoint:$summary1[0].endpoint,
      region:$summary1[0].region,
      gitSha:$summary1[0].gitSha,
      caseSetVersion:$summary1[0].caseSetVersion,
      casesSha256:$summary1[0].casesSha256,
      promptVersion:$summary1[0].promptVersion,
      schemaVersion:$summary1[0].schemaVersion,
      packVersion:$summary1[0].packVersion,
      scoringVersion:$summary1[0].scoringVersion,
      frozenModelVersion:$summary1[0].frozenModelVersion,
      inputMode:"IMAGE_ONLY_NO_TEXT_HINTS",
      silentRetries:false,
      runs:2,
      casesPerRun:($run1|length),
      calls:($all|length),
      scoredCalls:($scored|length),
      categoryCorrect:($scored|map(select(.correct))|length),
      categoryAccuracy:(($scored|map(select(.correct))|length)/($scored|length)),
      validResponses:($valid|length),
      schemaValidity:(($valid|length)/($all|length)),
      categoryCorrectOnValidResponses:($valid|map(select(.correct))|length),
      categoryAccuracyOnValidResponses:(($valid|map(select(.correct))|length)/($valid|length)),
      misclassifications:($valid|map(select(.correct==false))|length),
      stableCases:($pairs|map(select(.categoryStable))|length),
      categoryStability:(($pairs|map(select(.categoryStable))|length)/($pairs|length)),
      statusStability:(($pairs|map(select(.statusStable))|length)/($pairs|length)),
      clarificationStability:(($pairs|map(select(.clarificationStable))|length)/($pairs|length)),
      clarification:{requiredAcrossCalls:($all|map(select(.needsClarification==true or .status=="CLARIFICATION_REQUIRED"))|length)},
      failuresAcrossCalls:{
        none:($all|map(select(.failureClass=="NONE"))|length),
        timeout:($all|map(select(.failureClass=="TIMEOUT"))|length),
        model:($all|map(select(.failureClass=="MODEL"))|length),
        schema:($all|map(select(.failureClass=="SCHEMA"))|length),
        transport:($all|map(select(.failureClass=="TRANSPORT"))|length),
        http:($all|map(select(.failureClass=="HTTP"))|length)
      },
      namedFailureMode:{
        code:"IMAGE_PATH_SCHEMA_VALIDATION_INSTABILITY",
        layer:"POST_PROVIDER_LOCAL_CLASSIFICATION_SCHEMA_VALIDATION",
        occurrences:($schemaFailures|length),
        affectedCases:($schemaFailures|map(.caseId)|unique|length),
        repeatedCases:($schemaFailures|group_by(.caseId)|map(select(length>1)|{caseId:.[0].caseId,occurrences:length})),
        occurrenceDetail:$schemaFailures,
        exactValidatorSubcodeAvailable:false,
        rejectedProviderPayloadAvailable:false,
        explanation:"The provider call completed, but the generated classification was rejected by the stricter local validator. The deployed endpoint preserves only a public error envelope, so the historical rejected JSON and exact validator subcode cannot be recovered.",
        causalStatus:"OBSERVED_LAYER_NAMED_EXACT_SCHEMA_VIOLATION_UNRESOLVED"
      },
      confusionPairs:($scored|map(select(.correct==false and .actualIssueType!=""))|group_by([.expectedIssueType,.actualIssueType])|map({expectedIssueType:.[0].expectedIssueType,actualIssueType:.[0].actualIssueType,count:length})),
      latencyMs:{run1:latency($run1|map(.latencyMs)),run2:latency($run2|map(.latencyMs))},
      perCategory:($scored|group_by(.expectedIssueType)|map({issueType:.[0].expectedIssueType,calls:length,correct:(map(select(.correct))|length),accuracy:((map(select(.correct))|length)/length)})),
      rawResponsesPreservedPrivately:true,
      universalImageAccuracyClaim:false,
      resultFiles:[($run1File|split("/")|last),($run2File|split("/")|last)],
      runSummaryFiles:[($run1Summary|split("/")|last),($run2Summary|split("/")|last)]
    }
' > "$output_file"

jq . "$output_file"
