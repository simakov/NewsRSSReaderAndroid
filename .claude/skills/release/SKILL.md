---
name: release
description: Cuts a new GitHub release for this repo — bumps MINOR version (+1, patch reset to 0) from the latest vX.Y.Z tag, tags the current HEAD commit, builds a debug APK, and publishes a GitHub release with that APK attached and notes generated from every commit since the previous release, grouped by Conventional Commit type (feat/fix/etc). Use whenever the user asks to "release", "cut a release", "release a new version", "запусти релиз", "сделай релиз", "затегай версию", "подними версию и релизни", or otherwise wants a version tag + GitHub release (with APK) created from the current commit.
---

# Release

Cuts a new release of this Android app: computes the next version, tags the current
commit, publishes a GitHub release with auto-generated notes, and attaches a built
debug APK as a release asset.

## Versioning rule

This project uses `vMAJOR.MINOR.PATCH` tags (e.g. `v1.0.0`). Every run of this skill bumps
**MINOR by +1** and resets **PATCH to 0** — regardless of what PATCH was on the last tag
(e.g. `v1.0.0` → `v1.1.0`, `v1.4.7` → `v1.5.0`). This is a fixed project convention, not
something to infer per-run — don't bump MAJOR or PATCH instead, even if commit messages look
like breaking changes or small fixes.

## Steps

1. **Sanity-check repo state.**
   - Run `git status --porcelain` and `git branch --show-current`.
   - If there are uncommitted changes, tell the user they won't be part of the release
     (the tag points at HEAD, not the working tree) and confirm they want to proceed anyway.
   - If not on `main`, warn the user and confirm before continuing — releases are normally
     cut from `main`.
   - Confirm `gh auth status` succeeds; if not, tell the user to run `gh auth login` first
     and stop.

2. **Compute the next version.**
   ```bash
   ./.claude/skills/release/scripts/next_version.sh
   ```
   This prints `LAST_TAG=...` (empty if no prior tag exists) and `NEXT_VERSION=...` following
   the MINOR+1/PATCH→0 rule above.

3. **Build the release notes.**
   ```bash
   ./.claude/skills/release/scripts/build_notes.sh "<LAST_TAG>" "<NEXT_VERSION>"
   ```
   This walks every commit from `LAST_TAG..HEAD` (or full history if there's no prior tag),
   groups them by Conventional Commit type (`feat:`, `fix:`, `perf:`, `refactor:`, `docs:`,
   `test:`, `build:`, `ci:`, `style:`, `chore:`), and puts anything that doesn't match that
   prefix convention under "Other". It appends a GitHub compare-link footer. Save the output
   to a temp file (e.g. under the scratchpad directory) since it's passed to `gh release
   create --notes-file`.

   If the notes say "No commits since the previous release", tell the user there's nothing
   new to release and ask whether they still want an empty release — don't just proceed
   silently, since an empty release is almost never what's wanted.

4. **Build the APK.**
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # only if not already set
   ./gradlew :app:assembleDebug
   ```
   Use the **debug** build, not release: `app/build.gradle.kts` has no `signingConfig` on
   the `release` build type, so `assembleRelease` produces an unsigned APK that can't be
   installed — the debug build is signed with the debug key and is what this project's own
   `build_apk.sh` / CLAUDE.md already use for distributable local builds. Copy the output
   (`app/build/outputs/apk/debug/app-debug.apk`) to the scratchpad directory, renamed to
   include the version, e.g. `NewsRSSReader-<NEXT_VERSION>.apk`, since `gh release upload`
   uses the filename as the asset name and `app-debug.apk` on its own isn't useful across
   multiple releases. Do this before asking for confirmation so the plan shown to the user
   is complete and doesn't stall mid-release on a build failure.

5. **Present the plan and get confirmation before touching anything remote.**
   Show the user: the new tag name, the commit it will point at (`git rev-parse --short
   HEAD`), the generated release notes, and the built APK (path + size). Ask them to confirm
   before pushing the tag or creating the release — tagging and publishing a release are
   visible, hard-to-cleanly-undo actions (deleting a pushed tag/release after the fact is
   possible but messy if anyone already pulled it), so don't skip this even though the rest
   of the workflow is scripted.

6. **On confirmation, tag, release, and attach the APK.**
   ```bash
   git tag -a "<NEXT_VERSION>" -m "<NEXT_VERSION>"
   git push origin "<NEXT_VERSION>"
   gh release create "<NEXT_VERSION>" \
     --title "<NEXT_VERSION>" \
     --notes-file "<path-to-notes-file>"
   gh release upload "<NEXT_VERSION>" "<path-to-renamed-apk>"
   ```
   `gh release create` with an already-pushed tag will use that tag as-is rather than
   creating a new one, which keeps "tag the commit" and "publish the release" as distinct
   steps.

7. **Report the result.** Share the release URL that `gh release create` prints, the final
   version number, and confirm the APK asset is attached (`gh release view <NEXT_VERSION>
   --json assets --jq '.assets[].name'`).

## Notes

- The scripts assume the standard `vMAJOR.MINOR.PATCH` tag format already used in this repo
  (see `v1.0.0`). If tags ever stop following that pattern, `next_version.sh` will ignore
  tags that don't match `v[0-9]*.[0-9]*.[0-9]*` when finding the latest one.
- Commit-type grouping is a best-effort convenience, not a strict requirement on commit
  message style — commits that don't follow `type: subject` or `type(scope): subject` land
  under "Other" rather than being dropped or miscategorized.
- Written for bash 3.2 (macOS's default `/bin/bash`) — no associative arrays, and the regex
  used with `[[ =~ ]]` is stored in a variable first, since inlining it breaks under bash 3.2.
  Keep that in mind if editing the scripts.
