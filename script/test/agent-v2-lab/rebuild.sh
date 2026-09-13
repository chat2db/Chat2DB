#!/usr/bin/env bash
set -euo pipefail
lab_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
if [[ "${1:-}" != "--confirm-owned-schemas" || "$#" -ne 1 ]]; then
  printf 'Usage: bash rebuild.sh --confirm-owned-schemas\nOnly agent_v2_lab and agent_v2_scope_lab can be rebuilt.\n' >&2
  exit 1
fi
for schema in agent_v2_lab agent_v2_scope_lab; do
  exists="$(bash "$lab_dir/mysql.sh" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$schema'")"
  if [[ "$exists" == "1" ]]; then
    marker="$(bash "$lab_dir/mysql.sh" --batch --skip-column-names -e "SELECT CONCAT(owner,':',dataset_version,':',seed) FROM $schema._lab_manifest")"
    if [[ "$marker" != "chat2db-agent-v2-lab:1:fixed-2026-six-months" ]]; then
      printf 'Refusing to rebuild %s: fixture ownership marker does not match.\n' "$schema" >&2
      exit 1
    fi
  fi
done
# Both checks finish before either schema is removed. Names are fixed, never user supplied.
bash "$lab_dir/mysql.sh" -e 'DROP DATABASE IF EXISTS agent_v2_lab; DROP DATABASE IF EXISTS agent_v2_scope_lab;'
bash "$lab_dir/initialize.sh"
