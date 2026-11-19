# f-droid.org submission (M6)

`dev.co508.drums.yml` is a **draft**, not a submitted listing. It has no build
entries yet. In M6, adapt it to the final M4 application ID and a real stable
release, then submit it as `metadata/<applicationId>.yml` in a merge request to
<https://gitlab.com/fdroid/fdroiddata>. CI cannot push releases to f-droid.org.
See [deployment](../../docs/deployment.md).

- Keep the metadata **comment-free**: `fdroid rewritemeta` strips comments and
  fdroiddata CI fails on any resulting diff. Keep explanations here instead.
- `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` matches release-please's
  explicitly unprefixed stable tags and excludes all `beta-*` tags.
- `AutoUpdateMode: Version` reads Gradle's plain version literals.
- `AutoName` must match the app label. Store text comes from `fastlane/`, not
  duplicate Summary/Description fields.
- Resolve each initial `Builds.commit` to a full 40-character commit hash;
  tags/branches are mutable. Use a real released version/code, never the M0 seed.
- Before submission, run `fdroid rewritemeta dev.co508.drums` and
  `fdroid checkupdates dev.co508.drums` in a real fdroiddata checkout and confirm
  each leaves no diff. checkupdates requires a pushed stable release to inspect.
- Check the pinned daemon JDK is available on F-Droid's builder, and that
  `assembleRelease` works unsigned from a clean checkout. Reproducible-build
  signature sharing is a separate M6 decision, not assumed here.
