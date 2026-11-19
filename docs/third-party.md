# Third-party components

Everything the app ships that we didn't write. The licences screen (#18) is
maintained by hand from this list (AGENTS.md); update both when adding or
removing a shipped component. Test-only and build-only tools don't ship and
aren't listed.

| Component | Version | Licence | Used for |
| --- | --- | --- | --- |
| Bravura font, © Steinberg Media Technologies GmbH | 1.482 | SIL Open Font License 1.1 ([`licenses/Bravura-OFL.txt`](../licenses/Bravura-OFL.txt)) | Music symbols in the practice views (`res/font/bravura.otf`, unmodified) |
| Versilian Community Sample Library, Versilian Studios LLC | revision `c1ea7bc` | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Modified snare recordings and buzz bounces; [file provenance](../app/src/main/assets/samples/README.md) |
| Kotlin standard library | 2.3.20 | Apache 2.0 | Everything |
| kotlinx.serialization (JSON) | 1.11.0 | Apache 2.0 | Reading `pack.json` |
| AndroidX Core, Activity | see `gradle/libs.versions.toml` | Apache 2.0 | App framework |
| AndroidX DataStore | 1.2.1 | Apache 2.0 | Practice settings and view preference (JSON serializer) |
| AndroidX Room and its AndroidX SQLite dependencies | Room 2.8.5; SQLite 2.6.2 (transitive) | Apache 2.0 | Persistent practice session log and best tempos; uses the platform SQLite implementation |
| Jetpack Compose (UI, Material 3) | BOM 2026.08.00 | Apache 2.0 | User interface |
| kotlinx.coroutines (via Compose) | as resolved | Apache 2.0 | Background loading |

The built-in lessons are our own work, licensed CC BY-SA 4.0
([`lessons/README.md`](../lessons/README.md)).

Bravura is used unmodified. The OFL reserves its name, so a subset or other
modified copy would have to be renamed.

The Settings → About → Licenses and credits screen groups these components by
project and includes our GPL-3.0-only app and CC BY-SA lesson content. Full legal
texts in `licenses/` ship as APK assets and can be read offline. Apache 2.0 is
from apache.org/licenses/LICENSE-2.0.txt; CC0 and CC BY-SA are from their
creativecommons.org legalcode.txt URLs, and GPLv3 from gnu.org/licenses/gpl-3.0.txt.
Update both the hand-maintained project list in `ui/settings/LicensesScreen.kt`
and this file when shipped dependencies/assets change. No build-time downloads.
