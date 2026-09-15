# Desktop update packages

Community desktop checks the stable index at
`https://github.com/OtterMind/Chat2DB/releases/latest/download/release-index.json`.
Each signed manifest points to a full package attached to the same versioned
GitHub Release. The desktop verifies the product, platform, architecture,
package type, version, release sequence, Ed25519 signature, size and SHA-256.

## Build and release

Create an annotated source tag with a positive, increasing release sequence in
its annotation, on a line in this exact format:

```text
release_epoch: 1
```

Choose a sequence greater than the last published Community release. Include
the source commit and release inputs in the annotation so the build is
reproducible. Tag-triggered builds publish only after every platform's packages
and the Docker job succeed. Manual builds use the explicit `release_epoch`
workflow input and upload Actions artifacts without publishing a Release.

Configure these secrets in the corresponding release/test environment:

| Secret | Purpose |
| --- | --- |
| `COMMUNITY_UPDATE_KEY_ID` | Identifier of the Community Ed25519 key |
| `COMMUNITY_UPDATE_PUBLIC_KEY_B64` | Base64 DER public key bundled with the desktop |
| `COMMUNITY_UPDATE_SIGNING_PRIVATE_KEY_B64` | Base64 PEM private key used only by the manifest generator |
| `WIN_SERVER_IP`, `WIN_SERVER_USER`, `WIN_SSH_PRIVATE_KEY`, `HOST_KEY` | Existing Windows signing service connection and SSH host fingerprint |
| `REMOTE_SIGN_PATH`, `REMOTE_SIGN_SCRIPT` | Staging directory and signing script on that service |

Windows signing uploads each package to the existing remote signer, applies its
SHA-1 and SHA-256 signatures, and verifies the downloaded result before wrapping
or publishing. Configure these connection secrets in the Community release
environment.

The existing `COMMUNITY_MAC_*` secrets continue to sign and notarize macOS
packages. Use separate test keys for development builds.

The packaging script accepts `COMMUNITY_RELEASE_EPOCH`,
`COMMUNITY_UPDATE_KEY_ID`, and `COMMUNITY_UPDATE_PUBLIC_KEY_B64`. It builds the
shared updater and stages `tools/chat2db-updater.jar` plus `version.json` in
each native application. The helper is a standalone shaded artifact; the
application depends on the ordinary updater module JAR.

Windows packages are signed in order: MSI, then its Inno EXE wrapper. macOS
updates contain an archive captured from the signed application in the
notarized DMG. Linux updates use DEB, RPM or AppImage according to the installed
package type.

`prepare_community_update.sh` generates a platform's signed manifests and
packages. Release aggregation requires all nine platform/package targets,
checks the payload hashes and version fields, and generates the final index.
The original nine manually downloadable installer names remain available.

## Validation

Run the updater module tests with Maven tests enabled, including
`UpdatePackagingScriptIntegrationTest`, which generates temporary Ed25519
keys and exercises all nine package targets through the Java verifier. Run
`actionlint .github/workflows/jcef_release.yml` and shell syntax checks before
publishing. Native signing, installation and startup must also be verified on
their respective operating systems.

An existing desktop without this updater must first install a version that
includes it. Test an installed version A updating to B; successfully building B
alone does not verify automatic updates. The helper records success only after
both the trial and normal application report healthy startup. Installation or
startup failures are recorded in the product update log; there is no automatic
rollback.
