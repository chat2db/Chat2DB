#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 14 ]; then
    echo "Usage: $0 <version> <native-version> <product> <channel> <platform> <arch> <package-type> <package-file> <launcher-relative-path> <output-dir> <base-url> <release-epoch> <build-sha> <release-notes-url>" >&2
    exit 1
fi

VERSION="$1"
NATIVE_VERSION="$2"
PRODUCT="$3"
CHANNEL="$4"
PLATFORM="$5"
ARCH="$6"
PACKAGE_TYPE="$7"
PACKAGE_FILE="$8"
LAUNCHER_RELATIVE_PATH="$9"
OUTPUT_DIR="${10}"
BASE_URL="${11%/}"
RELEASE_EPOCH="${12}"
BUILD_SHA="${13}"
RELEASE_NOTES_URL="${14}"
KEY_ID="${CHAT2DB_UPDATE_KEY_ID:-}"
PRIVATE_KEY_FILE="${CHAT2DB_UPDATE_SIGNING_PRIVATE_KEY_FILE:-}"
PRIVATE_KEY_B64="${CHAT2DB_UPDATE_SIGNING_PRIVATE_KEY_B64:-}"
OPENSSL_BIN="${CHAT2DB_OPENSSL_BIN:-openssl}"

for command in jq "${OPENSSL_BIN}" tar; do
    command -v "${command}" >/dev/null 2>&1 || {
        echo "Error: required command not found: ${command}" >&2
        exit 1
    }
done
if [[ ! "${VERSION}" =~ ^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$ ]]; then
    echo "Error: version must be a semantic desktop version" >&2
    exit 1
fi
if [[ ! "${NATIVE_VERSION}" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    echo "Error: native version must be numeric major.minor.build" >&2
    exit 1
fi
if [[ ! "${PRODUCT}" =~ ^[A-Z][A-Z0-9_-]*$ ]]; then
    echo "Error: invalid product: ${PRODUCT}" >&2
    exit 1
fi
case "${CHANNEL}" in STABLE|BETA) ;; *) echo "Error: unsupported channel: ${CHANNEL}" >&2; exit 1 ;; esac
case "${PLATFORM}:${ARCH}" in
    MACOS:X64|MACOS:ARM64|WINDOWS:X64|LINUX:X64|LINUX:ARM64) ;;
    *) echo "Error: unsupported platform/architecture pair: ${PLATFORM}:${ARCH}" >&2; exit 1 ;;
esac
case "${PACKAGE_TYPE}" in
    MACOS_APP_ARCHIVE)
        if [ "${PLATFORM}" != "MACOS" ]; then
            echo "Error: MACOS_APP_ARCHIVE is only valid for macOS" >&2
            exit 1
        fi
        PACKAGE_EXTENSION="tar.gz"
        ;;
    WINDOWS_EXE)
        if [ "${PLATFORM}" != "WINDOWS" ]; then
            echo "Error: WINDOWS_EXE is only valid for Windows" >&2
            exit 1
        fi
        PACKAGE_EXTENSION="exe"
        ;;
    LINUX_APPIMAGE)
        if [ "${PLATFORM}" != "LINUX" ]; then
            echo "Error: LINUX_APPIMAGE is only valid for Linux" >&2
            exit 1
        fi
        PACKAGE_EXTENSION="AppImage"
        if [ "${LAUNCHER_RELATIVE_PATH}" != "." ]; then
            echo "Error: AppImage launcher path must be ." >&2
            exit 1
        fi
        ;;
    LINUX_DEB)
        if [ "${PLATFORM}" != "LINUX" ]; then
            echo "Error: LINUX_DEB is only valid for Linux" >&2
            exit 1
        fi
        PACKAGE_EXTENSION="deb"
        ;;
    LINUX_RPM)
        if [ "${PLATFORM}" != "LINUX" ]; then
            echo "Error: LINUX_RPM is only valid for Linux" >&2
            exit 1
        fi
        PACKAGE_EXTENSION="rpm"
        ;;
    *)
        echo "Error: unsupported full package type: ${PACKAGE_TYPE}" >&2
        exit 1
        ;;
esac
if [[ ! "${RELEASE_EPOCH}" =~ ^[0-9]+$ ]]; then
    echo "Error: release epoch must be a non-negative integer" >&2
    exit 1
fi
if [[ ! "${BUILD_SHA}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Error: build SHA must be a 40-character lowercase Git commit" >&2
    exit 1
fi
if [[ ! "${BASE_URL}" =~ ^https:// ]] || [[ ! "${RELEASE_NOTES_URL}" =~ ^https:// ]]; then
    echo "Error: package and release-notes URLs must use HTTPS" >&2
    exit 1
fi
if [ ! -s "${PACKAGE_FILE}" ]; then
    echo "Error: full package file is missing: ${PACKAGE_FILE}" >&2
    exit 1
fi
if [ -z "${KEY_ID}" ]; then
    echo "Error: CHAT2DB_UPDATE_KEY_ID is required" >&2
    exit 1
fi
if [ -z "${PRIVATE_KEY_FILE}" ] && [ -z "${PRIVATE_KEY_B64}" ]; then
    echo "Error: an Ed25519 signing private key is required" >&2
    exit 1
fi
if [ -n "${PRIVATE_KEY_FILE}" ] && [ -n "${PRIVATE_KEY_B64}" ]; then
    echo "Error: configure only one update signing private key source" >&2
    exit 1
fi

WORK_DIR=$(mktemp -d)
cleanup() { rm -rf "${WORK_DIR}"; }
trap cleanup EXIT
mkdir -p "${OUTPUT_DIR}"

if [ "${PACKAGE_TYPE}" = "MACOS_APP_ARCHIVE" ]; then
    ENTRY_LIST="${WORK_DIR}/archive-entries.txt"
    tar -tzf "${PACKAGE_FILE}" > "${ENTRY_LIST}"
    if [ ! -s "${ENTRY_LIST}" ] || awk '
        BEGIN { bad = 0 }
        /^\// || /^\.\.\// || /\/\.\.\// || ($0 != "package" && $0 !~ /^package\//) { bad = 1 }
        END { exit bad ? 0 : 1 }
    ' "${ENTRY_LIST}"; then
        echo "Error: full package archive must contain only the package/ root" >&2
        exit 1
    fi
    if [ "${LAUNCHER_RELATIVE_PATH}" = "." ] || [[ "${LAUNCHER_RELATIVE_PATH}" = /* ]] \
            || [[ "/${LAUNCHER_RELATIVE_PATH}/" == *"/../"* ]]; then
        echo "Error: launcher path must stay inside the full package" >&2
        exit 1
    fi
    tar -xzf "${PACKAGE_FILE}" -C "${WORK_DIR}"
    if [ ! -f "${WORK_DIR}/package/${LAUNCHER_RELATIVE_PATH}" ]; then
        echo "Error: full package launcher is missing: ${LAUNCHER_RELATIVE_PATH}" >&2
        exit 1
    fi
    VERSION_FILE=$(find "${WORK_DIR}/package" -type f -path '*/app/version.json' -print -quit)
    if [ -z "${VERSION_FILE}" ]; then
        echo "Error: full package does not contain installed version metadata" >&2
        exit 1
    fi
    if ! jq -e \
        --arg version "${VERSION}" \
        --argjson releaseEpoch "${RELEASE_EPOCH}" \
        --arg buildSha "${BUILD_SHA}" \
        '.version == $version and .releaseEpoch == $releaseEpoch and
         .buildSha == $buildSha' \
        "${VERSION_FILE}" >/dev/null; then
        echo "Error: package version.json does not match the update manifest inputs" >&2
        exit 1
    fi
fi

KEY_FILE="${PRIVATE_KEY_FILE}"
if [ -n "${PRIVATE_KEY_B64}" ]; then
    KEY_FILE="${WORK_DIR}/update-signing-key.pem"
    printf '%s' "${PRIVATE_KEY_B64}" | "${OPENSSL_BIN}" base64 -d -A > "${KEY_FILE}"
    chmod 600 "${KEY_FILE}"
fi
"${OPENSSL_BIN}" pkey -in "${KEY_FILE}" -noout >/dev/null

sha256_file() { "${OPENSSL_BIN}" dgst -sha256 -r "$1" | awk '{print $1}'; }
file_size() {
    if [ "$(uname -s)" = "Darwin" ]; then stat -f %z "$1"; else stat -c %s "$1"; fi
}

PACKAGE_SHA=$(sha256_file "${PACKAGE_FILE}")
PACKAGE_SIZE=$(file_size "${PACKAGE_FILE}")
PRODUCT_LOWER=$(printf '%s' "${PRODUCT}" | tr '[:upper:]' '[:lower:]')
PLATFORM_LOWER=$(printf '%s' "${PLATFORM}" | tr '[:upper:]' '[:lower:]')
ARCH_LOWER=$(printf '%s' "${ARCH}" | tr '[:upper:]' '[:lower:]')
PACKAGE_TYPE_LOWER=$(printf '%s' "${PACKAGE_TYPE}" | tr '[:upper:]' '[:lower:]' | tr '_' '-')
PACKAGE_NAME="package-${PRODUCT_LOWER}-${PLATFORM_LOWER}-${ARCH_LOWER}-${PACKAGE_TYPE_LOWER}.${PACKAGE_EXTENSION}"
MANIFEST_NAME="manifest-${PRODUCT_LOWER}-${PLATFORM_LOWER}-${ARCH_LOWER}-${PACKAGE_TYPE_LOWER}.json"
PACKAGE_URL="${BASE_URL}/${PACKAGE_NAME}"

jq -cnS \
    --argjson schemaVersion 2 \
    --argjson releaseEpoch "${RELEASE_EPOCH}" \
    --arg status ACTIVE \
    --arg product "${PRODUCT}" \
    --arg channel "${CHANNEL}" \
    --arg version "${VERSION}" \
    --arg nativeVersion "${NATIVE_VERSION}" \
    --arg buildSha "${BUILD_SHA}" \
    --arg platform "${PLATFORM}" \
    --arg arch "${ARCH}" \
    --arg updateScope FULL_PACKAGE \
    --arg packageType "${PACKAGE_TYPE}" \
    --arg packageUrl "${PACKAGE_URL}" \
    --argjson packageSize "${PACKAGE_SIZE}" \
    --arg packageSha256 "${PACKAGE_SHA}" \
    --arg launcherRelativePath "${LAUNCHER_RELATIVE_PATH}" \
    --argjson updaterProtocolVersion 3 \
    --argjson minUpdaterProtocolVersion 3 \
    --arg releaseNotesUrl "${RELEASE_NOTES_URL}" \
    --arg keyId "${KEY_ID}" \
    '{schemaVersion: $schemaVersion, releaseEpoch: $releaseEpoch, status: $status,
      product: $product, channel: $channel, version: $version, nativeVersion: $nativeVersion, buildSha: $buildSha,
      platform: $platform, arch: $arch, updateScope: $updateScope, packageType: $packageType,
      packageUrl: $packageUrl, packageSize: $packageSize, packageSha256: $packageSha256,
      launcherRelativePath: $launcherRelativePath, updaterProtocolVersion: $updaterProtocolVersion,
      minUpdaterProtocolVersion: $minUpdaterProtocolVersion,
      releaseNotesUrl: $releaseNotesUrl, keyId: $keyId}' \
    > "${WORK_DIR}/canonical-manifest.json"
perl -pi -e 'chomp if eof' "${WORK_DIR}/canonical-manifest.json"

"${OPENSSL_BIN}" pkeyutl -sign -rawin -inkey "${KEY_FILE}" \
    -in "${WORK_DIR}/canonical-manifest.json" -out "${WORK_DIR}/manifest.sig"
SIGNATURE=$("${OPENSSL_BIN}" base64 -A -in "${WORK_DIR}/manifest.sig")
jq --arg signature "${SIGNATURE}" '. + {signature: $signature}' \
    "${WORK_DIR}/canonical-manifest.json" > "${OUTPUT_DIR}/${MANIFEST_NAME}"
cp "${PACKAGE_FILE}" "${OUTPUT_DIR}/${PACKAGE_NAME}"

DERIVED_PUBLIC_KEY=$("${OPENSSL_BIN}" pkey -in "${KEY_FILE}" -pubout -outform DER | "${OPENSSL_BIN}" base64 -A)
if [ -n "${CHAT2DB_UPDATE_PUBLIC_KEY_B64:-}" ] && [ "${DERIVED_PUBLIC_KEY}" != "${CHAT2DB_UPDATE_PUBLIC_KEY_B64}" ]; then
    echo "Error: signing key does not match CHAT2DB_UPDATE_PUBLIC_KEY_B64" >&2
    exit 1
fi

echo "Generated full update package: ${OUTPUT_DIR}/${PACKAGE_NAME}"
echo "Generated signed full-package manifest: ${OUTPUT_DIR}/${MANIFEST_NAME}"
