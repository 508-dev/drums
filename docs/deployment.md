# Releases and publishing

Ratamatap ships no server. The Android release machinery is adopted now; actual
v0.1 distribution starts in **M4**, after the final name/ID, artwork, signing and
device checks. No tags, releases, signing keys or secrets are created by M0
agent work. The seed `0.0.1` is not a distributable release. AGP rejects the
versionCode 0 that the requested 0.0.0 seed would derive, so M0 uses 0.0.1
without changing the version formula; the first feature release will be 0.1.0.

## Release and versioning

```text
merge a Conventional Commit PR into main
  -> release-please opens/updates a standing release PR
     (version.txt + CHANGELOG.md)
  -> workflow syncs derived Gradle literals and store notes onto that PR
merge the release PR deliberately
  -> release-please tags vX.Y.Z and creates the GitHub Release
  -> same workflow builds a signed APK from that tag
     -> APK + SHA-256 attached to GitHub Release
     -> APK added to self-hosted F-Droid repository on gh-pages
```

Normal main merges do not publish. Both jobs share one workflow run because
tags/PRs created with `GITHUB_TOKEN` don't trigger another workflow. Release
concurrency queues runs rather than cancelling a publish halfway through.
The publish checkout uses the tag, not a drifting main tip.

PR titles become squash commit subjects. `feat:` bumps minor; `fix:`/`perf:`
bump patch. Breaking changes bump minor before 1.0, major after. Docs, chore,
CI, build, refactor, style and test changes alone do not create a release.
`pr-title.yml` validates this shape without a third-party regex action.
`.release-please-config.json` explicitly disables component prefixes, so stable
tags are always `vX.Y.Z`. No manual baseline tag is needed for the M0 seed:
`.release-please-manifest.json` starts at `0.0.1`.

`version.txt` is the sole version source; release-please owns it and CHANGELOG.
`scripts/sync-version.sh` derives:

- `versionCode = major * 1000000 + minor * 1000 + patch`;
- `versionName`, equal to the bare semver;
- `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.

Run `./scripts/sync-version.sh` after a deliberate version change and commit its
outputs. CI runs `--check`. Keep both Gradle version values **plain literals,
each on its own line**: F-Droid's update bot parses them textually. Never use
an expression or choose a versionCode by hand. Minor/patch must not exceed 999.
Store version codes must rise; Play also permanently rejects reused codes.
Beta uses workflow-run overrides only on beta outputs; see [beta builds](beta-builds.md).

## Store publishing

### Signing and channel identity

| Channel | Signing identity |
| --- | --- |
| GitHub APK and self-hosted F-Droid | Ratamatap release key, also used to sign the repository index |
| Beta | Separate Ratamatap beta key; debug-key fallback for local/credential-free compile checks |
| f-droid.org (M6) | F-Droid's key by default; reproducible-build sharing is not configured |
| Google Play (decision in M7) | Play App Signing if adopted; not configured or uploaded by CI |

Android will not update an installation with a differently signed APK. The
GitHub/self-hosted builds will be interchangeable, but ordinary f-droid.org
builds will require uninstall/reinstall to switch. Reproducible builds are a
possible later way to share the release signature, not an M0 promise.

CI supplies `RATAMATAP_KEYSTORE_FILE`, `RATAMATAP_KEYSTORE_PASSWORD`, `RATAMATAP_KEY_ALIAS`
and `RATAMATAP_KEY_PASSWORD`. Local `keystore.properties` (see its example) takes
precedence when complete. With neither configured, release builds are unsigned,
as needed by source-building distributors. No release keystore means the
workflow still tags/writes notes but skips binary publication.

Release and beta enable R8/resource shrinking. Smoke-test a shrunk build on a
device before shipping; compilation alone does not prove runtime behavior.

### Self-hosted F-Droid (M4)

`scripts/fdroid-publish.sh` maintains a cumulative `gh-pages` repository:
`https://508-dev.github.io/ratamatap/fdroid/repo`. This URL is not claimed live yet.
Old APKs stay indexed. The branch contains metadata, APKs, signed indexes and
a landing page publishing the URL and SHA-256 signing fingerprint. Users should
pin that fingerprint rather than trust the URL alone.

Inputs are `fdroid/config.yml`, `fdroid/metadata/dev.co508.ratamatap.yml` and
`fastlane/metadata/android/`. The resolved signing config is never committed to
gh-pages. Enable GitHub Pages from that branch/root after the first publication.
Store text, release notes and future screenshots live only in fastlane.

### f-droid.org (M6)

The draft `fdroid/fdroiddata/dev.co508.ratamatap.yml` is a one-time submission
payload, not our self-hosted metadata. Update it for a real release/final app ID
and submit a merge request to <https://gitlab.com/fdroid/fdroiddata> in M6. Nothing
can be pushed there from our CI. Their bot builds on its own schedule.

The payload must have no comments because `fdroid rewritemeta` strips them and
their CI rejects any diff. `AutoUpdateMode: Version` reads our literals;
`UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` excludes beta tags. Use full
commit hashes for initial build entries. Before submission, validate rewritemeta
and checkupdates against the real release, check JDK 25 provisioning works on
their builder, and verify `assembleRelease` works without signing properties.
See [submission notes](../fdroid/fdroiddata/README.md).

### Google Play (deferred)

There is **no Play upload step or Play service-account secret** in this app.
M7 decides Play together with paid packs/billing and a separate F-Droid-compatible
flavor. If adopted later, the first upload must be manual, choose the signing
identity at app creation, retain R8 mappings, and restrict automation to internal
testing until the maintainer deliberately promotes it. Store copy should still
come from fastlane. Do not set up Play as part of M0.

## Secrets and one-time maintainer setup

Agents never create signing keys or secrets, run `setup-beta-signing.sh`, push
tags, create releases or make store submissions during this milestone.

1. Enable squash merging with **PR title** as commit message and allow Actions
   to create release PRs. Review branch protection/check names with the maintainer.
2. The existing beta key was created with slug `drums`; never regenerate it.
   Its alias `drums-beta` and the four existing repo-level `BETA_*` secrets
   remain valid. The setup helper is human-only; never reuse release keys.
3. In M4, the maintainer creates a Ratamatap-only release key and backs it up offline,
   along with passwords. Losing it breaks in-place updates; rotating it also
   invalidates the self-hosted F-Droid fingerprint. Do not generate one casually.
4. Store release secrets and `RELEASE_PLEASE_TOKEN` under the GitHub **Prod
   environment**, matching `environment: Prod` in release.yml. Beta's four
   secrets are **repo-level**, never Prod: arbitrary branch workflows must not
   see production credentials. Missing/wrong-scope secrets otherwise look empty.
5. The release-please token should be a fine-grained PAT for `508-dev/ratamatap` only:
   **Contents read/write** and **Pull requests read/write**. Without it, fallback
   `GITHUB_TOKEN` creates a release PR without automatically triggered CI; do not
   merge unchecked releases.

Use password-manager paths `508/android/ratamatap/<EXACT_GITHUB_SECRET_NAME>` for
release credentials/token; never put secret values in the repo, logs or process
arguments. PKCS12 uses one password for store and key, so the two password secret
names carry the same value. The full table is in [secrets](secrets.md).
