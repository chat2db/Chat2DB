#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -lt 5 ]; then
    echo "Usage: $0 <channel> <release-epoch> <manifest-base-url> <output-file> <manifest>..." >&2
    exit 1
fi

CHANNEL="$1"
RELEASE_EPOCH="$2"
MANIFEST_BASE_URL="${3%/}"
OUTPUT_FILE="$4"
shift 4

case "${CHANNEL}" in STABLE|BETA) ;; *) echo "Error: unsupported channel: ${CHANNEL}" >&2; exit 1 ;; esac
if [[ ! "${RELEASE_EPOCH}" =~ ^[0-9]+$ ]]; then
    echo "Error: release epoch must be a non-negative integer" >&2
    exit 1
fi
if [[ ! "${MANIFEST_BASE_URL}" =~ ^https:// ]]; then
    echo "Error: manifest base URL must use HTTPS" >&2
    exit 1
fi

REFERENCES=$(mktemp)
trap 'rm -f "${REFERENCES}"' EXIT
: > "${REFERENCES}"
EXPECTED_PRODUCT=""
EXPECTED_VERSION=""

for manifest in "$@"; do
    if [ ! -f "${manifest}" ]; then
        echo "Error: update manifest is missing: ${manifest}" >&2
        exit 1
    fi
    status=$(jq -r '.status' "${manifest}")
    schema_version=$(jq -r '.schemaVersion' "${manifest}")
    manifest_channel=$(jq -r '.channel' "${manifest}")
    manifest_epoch=$(jq -r '.releaseEpoch' "${manifest}")
    product=$(jq -r '.product' "${manifest}")
    version=$(jq -r '.version' "${manifest}")
    if [ "${schema_version}" != "2" ] || [ "${status}" != "ACTIVE" ] || [ "${manifest_channel}" != "${CHANNEL}" ] || \
       [ "${manifest_epoch}" != "${RELEASE_EPOCH}" ]; then
        echo "Error: manifest release state does not match the promoted channel: ${manifest}" >&2
        exit 1
    fi
    if [ -z "$(jq -r '.signature // empty' "${manifest}")" ]; then
        echo "Error: promoted manifest is unsigned: ${manifest}" >&2
        exit 1
    fi
    if [ -z "${EXPECTED_PRODUCT}" ]; then
        EXPECTED_PRODUCT="${product}"
        EXPECTED_VERSION="${version}"
    elif [ "${product}" != "${EXPECTED_PRODUCT}" ] || [ "${version}" != "${EXPECTED_VERSION}" ]; then
        echo "Error: all promoted manifests must have the same product and version" >&2
        exit 1
    fi
    jq -cn \
        --arg version "${version}" \
        --arg platform "$(jq -r '.platform' "${manifest}")" \
        --arg arch "$(jq -r '.arch' "${manifest}")" \
        --arg packageType "$(jq -r '.packageType' "${manifest}")" \
        --arg manifestUrl "${MANIFEST_BASE_URL}/$(basename "${manifest}")" \
        '{version: $version, platform: $platform, arch: $arch,
          packageType: $packageType, manifestUrl: $manifestUrl}' \
        >> "${REFERENCES}"
done

reference_count=$(wc -l < "${REFERENCES}" | tr -d ' ')
if [ "${reference_count}" -ne 9 ]; then
    echo "Error: a desktop release must promote exactly nine full-package manifests, found ${reference_count}" >&2
    exit 1
fi
duplicate_count=$(jq -sr 'group_by(.platform + ":" + .arch + ":" + .packageType) | map(select(length > 1)) | length' "${REFERENCES}")
if [ "${duplicate_count}" -ne 0 ]; then
    echo "Error: promoted manifests contain duplicate platform/architecture/package-type tuples" >&2
    exit 1
fi
new_pairs=$(jq -sr 'map(.platform + ":" + .arch + ":" + .packageType) | sort | join(",")' "${REFERENCES}")
expected_pairs="LINUX:ARM64:LINUX_APPIMAGE,LINUX:ARM64:LINUX_DEB,LINUX:ARM64:LINUX_RPM,LINUX:X64:LINUX_APPIMAGE,LINUX:X64:LINUX_DEB,LINUX:X64:LINUX_RPM,MACOS:ARM64:MACOS_APP_ARCHIVE,MACOS:X64:MACOS_APP_ARCHIVE,WINDOWS:X64:WINDOWS_EXE"
if [ "${new_pairs}" != "${expected_pairs}" ]; then
    echo "Error: promoted manifests do not cover all supported native full-package targets: ${new_pairs}" >&2
    exit 1
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"
CURRENT_REFERENCES=$(mktemp)
PREVIOUS_REFERENCES=$(mktemp)
trap 'rm -f "${REFERENCES}" "${CURRENT_REFERENCES}" "${PREVIOUS_REFERENCES}"' EXIT
jq -s '.' "${REFERENCES}" > "${CURRENT_REFERENCES}"
printf '[]\n' > "${PREVIOUS_REFERENCES}"
if [ -n "${CHAT2DB_PREVIOUS_UPDATE_INDEX:-}" ]; then
    if [ ! -s "${CHAT2DB_PREVIOUS_UPDATE_INDEX}" ]; then
        echo "Error: previous updater-v2 channel index is missing" >&2
        exit 1
    fi
    if ! jq -e \
        --arg channel "${CHANNEL}" \
        --argjson releaseEpoch "${RELEASE_EPOCH}" \
        '.schemaVersion == 2 and .channel == $channel and
         (.releaseEpoch | type == "number") and .releaseEpoch <= $releaseEpoch and
         (.releases | type == "array") and
         all(.releases[];
           (.version | type == "string") and
           (.platform | IN("MACOS", "WINDOWS", "LINUX")) and
           (.arch | IN("X64", "ARM64")) and
           (.packageType | IN("MACOS_APP_ARCHIVE", "WINDOWS_EXE", "LINUX_APPIMAGE", "LINUX_DEB", "LINUX_RPM")) and
           (.manifestUrl | type == "string") and (.manifestUrl | startswith("https://")))' \
        "${CHAT2DB_PREVIOUS_UPDATE_INDEX}" >/dev/null; then
        echo "Error: previous updater-v2 channel index is invalid or newer than this release" >&2
        exit 1
    fi
    jq '.releases' "${CHAT2DB_PREVIOUS_UPDATE_INDEX}" > "${PREVIOUS_REFERENCES}"
fi
jq -n \
    --argjson schemaVersion 2 \
    --argjson releaseEpoch "${RELEASE_EPOCH}" \
    --arg status ACTIVE \
    --arg channel "${CHANNEL}" \
    --arg currentVersion "${EXPECTED_VERSION}" \
    --slurpfile previous "${PREVIOUS_REFERENCES}" \
    --slurpfile current "${CURRENT_REFERENCES}" \
    '{schemaVersion: $schemaVersion, releaseEpoch: $releaseEpoch, status: $status,
      channel: $channel,
      releases: ((($previous[0] | map(select(.version != $currentVersion))) + $current[0])
        | unique_by(.version + ":" + .platform + ":" + .arch + ":" + .packageType)
        | sort_by(.version, .platform, .arch, .packageType))}' \
    > "${OUTPUT_FILE}"

echo "Generated updater-v2 channel index: ${OUTPUT_FILE}"
