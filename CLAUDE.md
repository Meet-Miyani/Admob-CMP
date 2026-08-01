# CLAUDE.md — admob-compose-multiplatform

**Read [AGENTS.md](AGENTS.md) first** — it is the authoritative guide for
this repository (repo map, pre-PR protocol, public API rules, release
mechanics, docs site). This file only adds rules specific to Claude Code
that do not belong in `AGENTS.md`. Do not duplicate `AGENTS.md` content
here; if the rules change, update `AGENTS.md`, not this file.

## Claude-specific rules

- The pre-PR confirmation step is a **hard stop**. A clean
  `READINESS: PASS` is a prerequisite for *asking* the owner whether to
  open the PR — it is not authorisation to open it. Ask, wait for a
  reply, and do not act unilaterally.
- Do not run `scripts/release-readiness.sh` speculatively on unrelated
  turns. It is a long macOS build (Xcode + iOS + Astro); only run it
  when the user has asked for a pre-PR check, a release-readiness
  confirmation, or asked you to verify a branch.
- Do not edit `gradle.properties` or
  `admob-cmp-gradle-plugin/gradle.properties` `VERSION_NAME` unless the
  owner has explicitly asked for a release. The two must be bumped in
  lockstep and that decision belongs to the owner.
- **Do not propose adding test jobs to `.github/workflows/release.yml`.**
  Keeping CI free of SDK tests is a standing decision the owner has
  reaffirmed twice — including for the Ubuntu-only Gradle checks, so
  "it would only cost cheap Ubuntu minutes" is not a new argument. If
  coverage needs to move, it moves into `scripts/release-readiness.sh`.
- Do not introduce new files under `gradle/`, new secrets, or new
  Gradle dependencies without the owner's approval.
