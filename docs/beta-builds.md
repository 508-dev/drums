# Beta builds

Every push to a branch other than `main`, `gh-pages` or `release-please--...`
builds **Drums Beta**. Manual dispatch is also available. With all beta secrets
configured it publishes a GitHub pre-release and removes older betas for that
branch. Branch deletion removes its betas once the workflow is on `main`.

The app ID is `dev.co508.drums.beta`, label Drums Beta, with an orange launcher
background and a separate signing key. It installs beside production and debug.
The workflow run number is the beta `versionCode`; `versionName` is
`<version.txt>-beta.<run>+<short sha>`. Overrides affect beta outputs only.

## Install (once publishing is configured)

Download your branch's APK from <https://github.com/508-dev/drums/releases>.
Look for **Pre-release** and a title starting `Beta`.

For Obtainium:

1. Add `https://github.com/508-dev/drums`.
2. Enable **Include prereleases**.
3. Set **Filter APKs by regular expression** to `drums-beta`.
4. Set **Filter release titles by regular expression** to `^Beta`.

All branch betas share one application ID/key, so installing another branch
updates the same beta app, not a separate install for each branch.

## Signing

The maintainer must create the separate beta key and repo-level
`BETA_KEYSTORE_BASE64`, `BETA_KEYSTORE_PASSWORD`, `BETA_KEY_ALIAS` and
`BETA_KEY_PASSWORD` secrets. Agents never create them or execute
`scripts/setup-beta-signing.sh`. No key/secret setup is part of M0 agent work.

Without the keystore secret, CI runs `assembleBeta` as a compile check and skips
publishing. Local beta builds without `DRUMS_BETA_KEYSTORE_*` credentials use
the Android debug key; these cannot update a separately signed published beta.
Losing the beta key requires beta users to uninstall/reinstall. Production keys
and the `Prod` environment must never be available to branch beta jobs.
