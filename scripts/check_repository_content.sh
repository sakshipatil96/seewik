#!/usr/bin/env bash
set -euo pipefail

blocked=(
  'Chat'"GPT"
  'Open'"AI"
  'Cod'"ex"
  'internal '"tooling"
  'internal-'"tooling"
  'made by an '"assistant"
  'built by an '"assistant"
  'created by an '"assistant"
)

failed=0
for term in "${blocked[@]}"; do
  if rg -n -i -F --hidden \
      --glob '!.git/**' \
      --glob '!backend/target/**' \
      --glob '!frontend/node_modules/**' \
      --glob '!frontend/dist/**' \
      -- "$term" .; then
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo 'Repository content policy check failed.' >&2
  exit 1
fi

echo 'Repository content policy check passed.'
