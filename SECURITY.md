# Security

Report vulnerabilities privately to **caleb@508.dev**, not a public issue.
Include affected commit/version, reproduction steps and impact; omit passwords,
keystores and personal device data. Security support currently targets `main`.

The app has no network code or permission. Its one dangerous runtime permission
is the microphone (`RECORD_AUDIO`, from v0.2.0), for hit detection. It is asked
for only when the player calibrates or scores, and the audio is analysed in
memory and never stored or sent; only debug builds can save raw mic recordings,
to app storage. AndroidX adds an app-private signature permission for receivers.
Later imports must treat files as untrusted input. Shipped dependencies must
be free software; no telemetry, ads or proprietary SDKs are allowed.

Release identity depends on signing keys. Keep keystores and
`keystore.properties` out of git and logs. Beta credentials must remain separate
from the `Prod` environment. If a credential leaks, notify the maintainer
privately: removing it from history alone does not revoke it. Signing-key loss
can prevent in-place updates; do not rotate keys casually. See [secrets](docs/secrets.md).
