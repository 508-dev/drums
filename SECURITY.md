# Security

Report vulnerabilities privately to **caleb@508.dev**, not a public issue.
Include affected commit/version, reproduction steps and impact; omit passwords,
keystores and personal device data. Security support currently targets `main`.

The skeleton requests no dangerous runtime permissions and has no network or
recording code. AndroidX adds an app-private signature permission for receivers.
Later imports must treat files as untrusted input. Shipped dependencies must
be free software; no telemetry, ads or proprietary SDKs are allowed.

Release identity depends on signing keys. Keep keystores and
`keystore.properties` out of git and logs. Beta credentials must remain separate
from the `Prod` environment. If a credential leaks, notify the maintainer
privately: removing it from history alone does not revoke it. Signing-key loss
can prevent in-place updates; do not rotate keys casually. See [secrets](docs/secrets.md).
