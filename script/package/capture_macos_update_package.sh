#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <notarized-dmg> <output-tar-gz>" >&2
  exit 1
fi
DMG_PATH="$1"
OUTPUT_FILE="$2"
WORK_DIR=$(mktemp -d)
MOUNT_DIR="${WORK_DIR}/mount"
mkdir -p "${MOUNT_DIR}" "${WORK_DIR}/package"
cleanup() {
  hdiutil detach "${MOUNT_DIR}" -quiet >/dev/null 2>&1 || true
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT
hdiutil attach -nobrowse -readonly -mountpoint "${MOUNT_DIR}" "${DMG_PATH}" >/dev/null
shopt -s nullglob
apps=("${MOUNT_DIR}"/*.app)
if [ "${#apps[@]}" -ne 1 ]; then
  echo "Expected exactly one application in DMG" >&2
  exit 1
fi
codesign --verify --deep --strict "${apps[0]}"
/usr/bin/ditto "${apps[0]}/Contents" "${WORK_DIR}/package/Contents"
test -s "${WORK_DIR}/package/Contents/app/version.json"
test -s "${WORK_DIR}/package/Contents/app/tools/chat2db-updater.jar"
mkdir -p "$(dirname "${OUTPUT_FILE}")"
tar -czf "${OUTPUT_FILE}" -C "${WORK_DIR}" package
