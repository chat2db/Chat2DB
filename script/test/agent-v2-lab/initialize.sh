#!/usr/bin/env bash
set -euo pipefail
lab_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
python3 "$lab_dir/generate.py"
python3 "$lab_dir/generate.py" --check
existing="$(bash "$lab_dir/mysql.sh" --batch --skip-column-names -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('agent_v2_lab','agent_v2_scope_lab') ORDER BY SCHEMA_NAME")"
if [[ -n "$existing" ]]; then
  printf 'Initialization stopped: a target schema already exists: %s\nUse the guarded rebuild script only for schemas owned by this fixture.\n' "$existing" >&2
  exit 1
fi
bash "$lab_dir/mysql.sh" < "$lab_dir/00_schema.sql"
bash "$lab_dir/mysql.sh" < "$lab_dir/10_data.sql"
python3 "$lab_dir/verify_live.py"
