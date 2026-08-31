#!/usr/bin/env bash
set -euo pipefail

# Release diagnostics must query an allow-listed projection. A raw Cloud Run
# resource dump includes the revision template and can therefore print runtime
# environment values. Secret Manager payload access is never a diagnostic.
cloud_run_describe='gcloud run (services|revisions) describe'
unsafe_secret_access='gcloud secrets versions '"access"

failed=0
while IFS= read -r diagnostic_line; do
  if [[ "$diagnostic_line" != *'--format='* ]]; then
    echo "$diagnostic_line"
    echo 'Cloud Run diagnostic is missing an explicit allow-listed format projection.' >&2
    failed=1
    continue
  fi
  if [[ "$diagnostic_line" != *'json(status.traffic)'* && "$diagnostic_line" != *'value('* ]]; then
    echo "$diagnostic_line"
    echo 'Cloud Run diagnostic uses a non-allow-listed output projection.' >&2
    failed=1
  fi
  if rg -q -i -e '(^|[^[:alnum:]_])(env|secret|spec\.containers|spec\.template)([^[:alnum:]_]|$)' <<<"$diagnostic_line"; then
    echo "$diagnostic_line"
    echo 'Cloud Run diagnostic requests a protected runtime field.' >&2
    failed=1
  fi
done < <(rg -n -e "$cloud_run_describe" .github scripts --glob '!check_secret_safe_diagnostics.sh')

if rg -n -e "$unsafe_secret_access" .github scripts; then
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  echo 'Secret-safe diagnostics policy check failed.' >&2
  echo 'Use an allow-listed format projection and never print or fetch secret payloads in diagnostics.' >&2
  exit 1
fi

echo 'Secret-safe diagnostics policy check passed.'
