# GitHub workflows

| Workflow | Trigger | Result |
| --- | --- | --- |
| `ci.yml` | PRs, main pushes, manual | Version-drift check, ktlint, JVM tests, Android lint, debug APK artifact |
| `pr-title.yml` | PR open/edit/reopen/synchronize | Conventional Commit title check via regex |
| `release.yml` | main pushes, manual | Groom release PR; after its merge, publish signed GitHub/F-Droid APK when configured |
| `beta.yml` | non-main branch pushes, delete, manual | Compile beta; publish only with beta secrets; delete stale branch betas |

Actions are pinned to full commit SHAs copied from the Android convention pack
and soundboard. `harden-runner` audits egress. Validation uses read-only content
permissions and checkout without persisted credentials. Release/beta write
access is limited to the jobs that publish or manage release PRs.

Use squash merging with **PR title** as the squash commit subject. The title
gate protects release-please's version decisions. Release jobs use the `Prod`
environment; beta never uses it. See [deployment](deployment.md),
[secrets](secrets.md) and [beta builds](beta-builds.md).

Issue/PR templates and FUNDING remain under `.github/`. Optional CODEOWNERS,
Discussions, Gitleaks and Dependency Review examples remain in `extras/github/`.
Do not activate placeholder owners or security gates without maintainer review.
