# Contributing

Drums is at the Android skeleton stage. Read [DECISIONS.md](DECISIONS.md) and
the [roadmap](docs/roadmap.md) before proposing features; work follows ordered
milestone issues. Setup and device commands are in [development](docs/development.md).

Use a branch and open a focused PR. Commit subjects and PR titles follow
Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:` etc.); squash merging
uses the PR title and release-please reads it. Include scope, tests, risks and
screenshots for UI changes. Never merge a release PR without maintainer review.

Before review:

```bash
./scripts/check-all.sh
./scripts/sync-version.sh --check
./gradlew assembleBeta
```

Add JVM tests for logic changes. Say explicitly when device testing wasn't done;
a green build does not verify audio or timing. Keep dependencies free software
and pins in the Gradle version catalog. Do not commit signing keys or secrets.

Code contributions are GPL-3.0-only. Future built-in lesson content is
CC BY-SA 4.0; samples require verified CC0 or CC BY licensing and credits.
