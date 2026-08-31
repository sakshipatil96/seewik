#!/usr/bin/env bash
set -euo pipefail

project_id="${SEEWIK_GCP_PROJECT:-seewik}"
region="${SEEWIK_GCP_REGION:-asia-south1}"
service="${SEEWIK_CLOUD_RUN_SERVICE:-seewik-api}"
health_url="${SEEWIK_API_URL:-https://seewik-api-528138216934.asia-south1.run.app}/health"

# Request routing metadata only. Never dump the service or revision resource:
# both include runtime environment values.
traffic_json="$(gcloud run services describe "$service" --project "$project_id" --region "$region" --format='json(status.traffic)')"
revision="$(jq -r '.status.traffic[] | select((.percent // 0) == 100) | .revisionName' <<<"$traffic_json")"
test -n "$revision"

# These are the complete allow-list for release evidence.
metadata="$(gcloud run revisions describe "$revision" --project "$project_id" --region "$region" --format='value(metadata.labels.commit-sha,status.conditions[0].status,status.imageDigest)')"
IFS=$'\t' read -r git_sha ready image_digest <<<"$metadata"
test -n "$git_sha"
test "$ready" = 'True'
test -n "$image_digest"

curl --fail --silent --show-error "$health_url" >/dev/null

printf 'revision=%s\ngit_sha=%s\nready=%s\nimage_digest=%s\nhealth=PASS\n' \
  "$revision" "$git_sha" "$ready" "$image_digest"
