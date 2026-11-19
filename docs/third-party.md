# Third-party components

Everything the app ships that we didn't write. The licences screen (#18) is
maintained by hand from this list (AGENTS.md); update both when adding or
removing a shipped component. Test-only and build-only tools don't ship and
aren't listed.

| Component | Version | Licence | Used for |
| --- | --- | --- | --- |
| Bravura font, © Steinberg Media Technologies GmbH | 1.482 | SIL Open Font License 1.1 ([`licenses/Bravura-OFL.txt`](../licenses/Bravura-OFL.txt)) | Music symbols in the practice views (`res/font/bravura.otf`, unmodified) |
| Kotlin standard library | 2.3.20 | Apache 2.0 | Everything |
| kotlinx.serialization (JSON) | 1.11.0 | Apache 2.0 | Reading `pack.json` |
| AndroidX Core, Activity | see `gradle/libs.versions.toml` | Apache 2.0 | App framework |
| Jetpack Compose (UI, Material 3) | BOM 2026.08.00 | Apache 2.0 | User interface |
| kotlinx.coroutines (via Compose) | as resolved | Apache 2.0 | Background loading |

The built-in lessons are our own work, licensed CC BY-SA 4.0
([`lessons/README.md`](../lessons/README.md)).

Bravura is used unmodified. The OFL reserves its name, so a subset or other
modified copy would have to be renamed.
