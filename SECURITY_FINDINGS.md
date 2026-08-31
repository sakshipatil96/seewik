# Security findings

This record names credential-handling findings without storing any credential value. It exists so repeated diagnostic mistakes are treated as a pattern and prevented systematically.

## `oauth_client_secret_exposed_privately_rotation_deferred`

- Date recorded: 2026-08-29.
- Source: a private task-tool output during OAuth configuration diagnostics.
- Persistence check: the value was not committed or included in the frontend bundle.
- Status: rotation remains deferred under the Day 10 risk decision recorded in `DAY10_BUILD_LOG.md`; replace and retire the OAuth client before public launch.

## `attendance_code_secret_exposed_in_diagnostic_output_rotated`

- Date recorded: 2026-08-31.
- Source: a raw `gcloud run revisions describe` JSON diagnostic printed the complete Cloud Run revision resource, including runtime environment values, into the private task-tool output.
- Impact: the runtime HMAC secret protecting organiser attendance codes was exposed. No real Initiative was running, so rotation invalidated no live event code.
- Immediate remediation: the secret was rotated, a healthy replacement revision was created, and 100% of production traffic was moved to it. The previous value is inactive.
- Persistence audit: the previous value was not found in the working tree, Git history, local shell history, Day 12 Quality logs or Day 12 deployment logs. It remains in the private task transcript where the diagnostic output occurred and in access-controlled retired Cloud Run revision configuration; it is not present in repository or public application content.
- Prevention: release diagnostics now use allow-listed Cloud Run projections only. The deployment candidate lookup requests `status.traffic` rather than the full service resource. CI rejects raw JSON/YAML Cloud Run description commands and Secret Manager payload access in workflows or repository scripts.

## Operating rule

Never print, log or persist a complete Cloud Run service/revision resource or a Secret Manager value. Release evidence is limited to revision name, commit label, Ready state, image digest, traffic metadata and health status. Use `scripts/describe_production_release.sh` for that evidence.
