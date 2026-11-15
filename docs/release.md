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

**Both names are attached.** Every release carries `ccc_X.Y.Z.apk` and, for the same file, the old `ccc.apk`:
installed app versions up to 0.2.4 look for the exact name `ccc.apk` and would otherwise never find a new
release. The app now looks for `ccc_<version>.apk` first and falls back to `ccc.apk` (`parseAppRelease` in
`AppUpdate.kt`, tests in `AppUpdateTest.kt`). ccc (TUI) never reads the app's releases, so
`tests/fixtures/release-rules.json` stays as it is; only ccc's `help/*/android-api.md` should mention the new name.
`SHA256SUMS` lists both names. Drop `ccc.apk` only when no installed app older than the rename is left.

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
