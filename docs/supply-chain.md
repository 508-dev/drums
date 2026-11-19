# Dependency supply chain

`renovate.json` enforces a **seven-day minimum release age** for update PRs and
understands Gradle version catalogs and Actions SHA pins. Gradle has no native
cooldown setting; Renovate is the enforcement point for routine updates.

- Use the committed Gradle wrapper, never a floating system Gradle. Its
  `distributionSha256Sum` verifies the pinned distribution download.
- Keep exact dependency/plugin versions in `gradle/libs.versions.toml`; do not
  add dynamic versions or unnecessary repositories. Repositories are centralized
  in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`).
- These pins are not full transitive dependency locking or artifact verification;
  do not claim they are. Review transitive dependencies and licenses on changes.
- Keep third-party GitHub Actions pinned to full commit SHAs. Publishing also
  pins fdroidserver; its Python environment is release tooling, not an app stack.
- For an urgent manual update, review release notes, document the cooldown
  exception, update [tooling](tooling.md), and run the full checks and beta build.
- Every shipped dependency must be free software; no Play Services, Firebase,
  proprietary SDKs, analytics or ads. See `DECISIONS.md`.

Optional secret scanning and Dependency Review examples remain in
`extras/github/`; enabling them requires maintainer agreement and triage.
