#!/usr/bin/env bash
set -euo pipefail
# Existing local MySQL 8.4 test container. The password stays inside its process environment.
exec docker exec -i mysql sh -c '
  export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is unavailable in mysql container}"
  exec mysql -uroot --default-character-set=utf8mb4 "$@"
' sh "$@"
