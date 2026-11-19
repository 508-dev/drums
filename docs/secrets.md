# Signing secrets

No runtime API keys, backend credentials or `.env` contract exist. Agents never
create keys/secrets or execute `scripts/setup-beta-signing.sh`.

| Secret | Scope | Purpose / when absent |
| --- | --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Prod environment | App release key; without it release notes/tag only, no binaries |
| `RELEASE_KEYSTORE_PASSWORD` | Prod environment | Release keystore password; required when release key is configured |
| `RELEASE_KEY_ALIAS` | Prod environment | Alias inside release keystore |
| `RELEASE_KEY_PASSWORD` | Prod environment | Same as store password for PKCS12 |
| `RELEASE_PLEASE_TOKEN` | Prod environment | Repo-scoped PAT so release PR triggers CI; falls back to unchecked GITHUB_TOKEN PR |
| `BETA_KEYSTORE_BASE64` | Repo-level | Separate beta key; absent means compile-only, no beta publication |
| `BETA_KEYSTORE_PASSWORD` | Repo-level | Beta keystore password |
| `BETA_KEY_ALIAS` | Repo-level | `drums-beta` alias |
| `BETA_KEY_PASSWORD` | Repo-level | Same as beta store password for PKCS12 |

The workflow maps release credentials to `DRUMS_KEYSTORE_*` and beta credentials
to `DRUMS_BETA_KEYSTORE_*`. F-Droid index signing uses the release key via
`FDROID_KEYSTORE`, `FDROID_KEY_ALIAS`, `FDROID_KEYSTORE_PASS`, `FDROID_KEY_PASS`;
these are workflow environment mappings, not extra independent GitHub secrets.
Google Play secrets are intentionally absent until a later product decision.

Local release signing uses gitignored `keystore.properties` (see
`keystore.properties.example`); complete local properties win over env vars.
Without either source, release is unsigned. Debug and credential-free beta
checks use Android's standard debug key. Never read aloud/commit real properties,
keystores or secret values; stop if any is staged. See [deployment](deployment.md).

The maintainer backs release material up offline under a password-manager
convention such as `508/android/drums/RELEASE_KEYSTORE_BASE64` (exact GitHub
secret names). Losing/changing the release key prevents in-place updates and
changes the self-hosted F-Droid fingerprint. Beta key loss affects beta installs
only. No signing material is created or configured by this PR.
