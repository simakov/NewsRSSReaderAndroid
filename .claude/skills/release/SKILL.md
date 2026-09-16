---
name: release
description: Cuts a new GitHub release for this repo — bumps MINOR version (+1, patch reset to 0) from the latest vX.Y.Z tag, tags the current HEAD commit, builds a debug APK, and publishes a GitHub release with that APK attached and release notes rewritten as a plain-language, user-facing announcement (not a raw commit changelog) from every commit since the previous release. Use whenever the user asks to "release", "cut a release", "release a new version", "запусти релиз", "сделай релиз", "затегай версию", "подними версию и релизни", or otherwise wants a version tag + GitHub release (with APK) created from the current commit.
---

# Release

Cuts a new release of this Android app: computes the next version, tags the current
commit, publishes a GitHub release with a plain-language announcement rewritten from the
raw commit history (not a raw changelog), and attaches a built debug APK as a release asset.

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

3. **Build the raw technical commit list.**
   ```bash
   ./.claude/skills/release/scripts/build_notes.sh "<LAST_TAG>" "<NEXT_VERSION>"
   ```
   This walks every commit from `LAST_TAG..HEAD` (or full history if there's no prior tag),
   groups them by Conventional Commit type (`feat:`, `fix:`, `perf:`, `refactor:`, `docs:`,
   `test:`, `build:`, `ci:`, `style:`, `chore:`), and puts anything that doesn't match that
   prefix convention under "Other". It appends a GitHub compare-link footer. Save the output
   to a temp file (e.g. under the scratchpad directory).

   This raw, commit-message-grouped list is **input data for the next step, not what gets
   published** — it's full of implementation detail (file/class names, code-review jargon,
   "wire X into Y") that means nothing to someone who just uses the app.

   If the notes say "No commits since the previous release", tell the user there's nothing
   new to release and ask whether they still want an empty release — don't just proceed
   silently, since an empty release is almost never what's wanted.

4. **Rewrite the raw commit list into a user-facing release announcement.**
   Read every commit message in the raw list (including ones that ended up under "Other") and
   rewrite the release notes yourself, in plain language, for someone who has never touched
   the code and doesn't know what a `ViewModel` or a `FileProvider` is — as if a marketer were
   writing the announcement for this release, not a changelog for other engineers.

   - **Describe user-visible impact, not implementation.** Say what changed for someone using
     the app ("теперь можно...", "исправлено, что..."), not how it was built. E.g. a commit
     titled "add UpdateBanner to the bottom of the category drawer" becomes something like
     "В меню категорий появилось уведомление о новой версии приложения с кнопкой обновления."
     A commit titled "expose current git tag as BuildConfig.GIT_TAG" has **no** user-visible
     translation — it's pure plumbing for a feature described by a *different* commit in the
     same batch, so don't invent a bullet for it; fold it silently into the feature it supports
     instead of listing it separately.
   - **Omit what doesn't affect using the app.** Skip (don't just soften) anything that's pure
     internal restructuring with no user-facing effect: refactors, test additions, build/CI/
     gradle config changes, internal helper/plumbing commits, doc or plan commits, and
     meta-commits about the release process itself. A commit only earns a bullet if a real user
     would notice something different in the app because of it. It's normal and expected for
     several raw commits to collapse into one bullet, or to disappear entirely.
   - **Group by what the user experiences**, not by Conventional Commit type — a "новое"
     (new/improved) group and an "исправлено" (fixed) group is usually enough; don't feel
     bound to mirror `feat`/`fix`/`refactor` categories from the raw list.
   - **Write in the app's own UI language** (Russian, matching every string already in the
     app) unless the user asks for something else.
   - **Keep the "Full Changelog" compare-link footer** from the raw notes at the bottom
     unchanged — that's the escape hatch for anyone (including future-you) who wants the real
     technical diff.
   - If, after this filtering, there's nothing user-visible left to announce (e.g. a release
     that's 100% internal refactoring/tooling), say so plainly rather than padding the
     announcement with restated implementation detail — a short "Внутренние технические
     улучшения, без изменений в работе приложения" line plus the changelog link is better than
     manufacturing fake user-facing bullets — but such a release should be rare in practice.

   Save this rewritten announcement to its own file (e.g. `release-announcement-<NEXT_VERSION>.md`
   in the scratchpad directory, next to the raw notes) — this file, not the raw commit list, is
   what gets shown to the user for confirmation and passed to `gh release create --notes-file`
   in the steps below.

5. **Create the tag locally (do not push yet).**
   ```bash
   git tag -a "<NEXT_VERSION>" -m "<NEXT_VERSION>"
   ```
   This must happen **before** building the APK, not after: the app's `build.gradle.kts` bakes
   `git describe --tags --abbrev=0` into `BuildConfig.GIT_TAG` at build time, which the app
   compares against GitHub Releases to detect updates. If the APK were built first and tagged
   second (the old, buggy order), the shipped APK would report the *previous* tag and the app
   would immediately think a newer version (itself) is available. Tagging locally first —
   without pushing — makes the build pick up the correct tag while keeping the actual
   publish-visible action (the push) gated behind the confirmation step below, same as before.

6. **Build the APK.**
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

   If anything goes wrong here (or the user declines in the next step), delete the local tag
   (`git tag -d "<NEXT_VERSION>"`) before stopping — it was never pushed, so this is a fully
   clean, invisible-to-everyone-else rollback.

7. **Present the plan and get confirmation before touching anything remote.**
   Show the user: the new tag name, the commit it will point at (`git rev-parse --short
   HEAD`), the rewritten user-facing announcement from step 4 (not the raw commit list), and
   the built APK (path + size). Ask them to confirm before pushing the tag or creating the
   release — tagging and publishing a release are visible, hard-to-cleanly-undo actions
   (deleting a pushed tag/release after the fact is possible but messy if anyone already
   pulled it), so don't skip this even though the rest of the workflow is scripted. If they
   decline, delete the local tag (`git tag -d "<NEXT_VERSION>"`) and stop — nothing has
   touched the remote yet.

8. **On confirmation, push the tag, release, and attach the APK.**
   ```bash
   git push origin "<NEXT_VERSION>"
   gh release create "<NEXT_VERSION>" \
     --title "<NEXT_VERSION>" \
     --notes-file "<path-to-announcement-file>"
   gh release upload "<NEXT_VERSION>" "<path-to-renamed-apk>"
   ```
   `--notes-file` points at the **rewritten announcement from step 4**, not the raw
   `build_notes.sh` output from step 3. `gh release create` with an already-pushed tag will
   use that tag as-is rather than creating a new one, which keeps "tag the commit" and
   "publish the release" as distinct steps.

9. **Report the result.** Share the release URL that `gh release create` prints, the final
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
