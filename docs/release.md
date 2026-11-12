# Releasing ccc for Android

Read this before you tag, build, sign or publish anything.

## Language

**All text written for a release is in English**: the release title, the release notes, the tag message,
the commit messages of the release, and any text shown to users in connection with it. (The rest of the
project, such as PR descriptions, may be in Czech.)

## File names carry the version

The file a user downloads is named `ccc_X.Y.Z.apk`, where `X.Y.Z` is the version of the tag (no `v`),
for example `ccc_0.2.3.apk`. Otherwise the user cannot tell which version they have downloaded.
`SHA256SUMS` must list that name (`sha256sum -c` has to pass); `SHA256SUMS` and `SHA256SUMS.asc` keep
their names.

**Current state:** releases up to 0.2.3 attach `ccc.apk`. The rename is not done yet. It cannot be done
in the release script alone: the app finds updates by the exact asset name (`APK_NAME` in
`AppUpdate.kt`; also `ApkInstaller.kt`, `AppViewModel.kt` and `AppUpdateTest.kt`), and it shares its rules
with ccc through `tests/fixtures/release-rules.json` (ccc keeps the original in `ccc_TUI`). So:

1. Teach the updater to accept `ccc_<version>.apk` — the same rule in the app and in ccc, tests in both.
2. For a transition period attach **both** `ccc_X.Y.Z.apk` and the old `ccc.apk`; otherwise installed
   older versions do not find the new release and never update themselves.
3. Update `README.md`, `SECURITY.md` and ccc's `help/*/android-api.md` (they name `ccc.apk`).

## Steps

1. Everything for the release is merged to `main`; tests pass (`./gradlew testDebugUnitTest`).
2. Tag `X.Y.Z` (no `v`) on `main` — `ccc-android-release.sh X.Y.Z` (in `~/Documents/CC/bin`), or create
   the release with `gh release create X.Y.Z --target <main commit>`.
3. The `Release` workflow builds and tests the app and keeps the **unsigned** APK as the artifact `apk`.
4. The maintainer signs on their own computer: `ccc-apk-sign-upload.sh X.Y.Z`. It signs the APK with the
   local key, signs `SHA256SUMS` with GPG and attaches the files to the release. The signing key never
   reaches GitHub.
5. If GitHub Actions is stuck, build locally —
   `./gradlew testDebugUnitTest assembleRelease -Pccc.version=X.Y.Z` — and pass the file with
   `ccc-apk-sign-upload.sh X.Y.Z --from-file <apk>`.
6. Check the release: the APK has the right `versionName`, the signature verifies, the release is "Latest".

## Release text

- Title: `ccc for Android X.Y.Z`.
- Notes: what changed for the user, in English, one line per change, with the issue number when there is one.
