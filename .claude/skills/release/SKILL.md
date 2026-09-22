---
name: release
description: Cuts a new GitHub release for this repo — bumps MINOR version (+1, patch reset to 0) from the latest vX.Y.Z tag, tags the current HEAD commit, builds a signed, minified release APK, and publishes a GitHub release with that APK attached and release notes rewritten as a plain-language, user-facing announcement (not a raw commit changelog) from every commit since the previous release. Use whenever the user asks to "release", "cut a release", "release a new version", "запусти релиз", "сделай релиз", "затегай версию", "подними версию и релизни", or otherwise wants a version tag + GitHub release (with APK) created from the current commit.
---

# Release

Cuts a new release of this Android app: computes the next version, tags the current
commit, publishes a GitHub release with a plain-language announcement rewritten from the
raw commit history (not a raw changelog), and attaches a signed release APK as a release asset.

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
     **One exception:** if `app/build.gradle.kts` or anything under
     `fastlane/metadata/android/*/changelogs/` is among them, stop — step 5 commits those, so
     their edits would be swept into the version-bump commit. Ask them to commit or stash first.
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
     A commit titled "expose the distribution channel as BuildConfig.UPDATE_CHECK_ENABLED" has
     **no** user-visible translation — it's pure plumbing for a feature described by a *different*
     commit in the same batch, so don't invent a bullet for it; fold it silently into the feature
     it supports instead of listing it separately. The same goes for the version-bump commit this
     skill makes in step 5.
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

5. **Bump the version literals, write the F-Droid changelog, commit, then tag that commit
   locally (do not push yet).**
   ```bash
   ./.claude/skills/release/scripts/bump_version.sh "<NEXT_VERSION>"   # prints VERSION_CODE
   # then write the two changelog files described below, and:
   git add app/build.gradle.kts fastlane/metadata/android/*/changelogs/
   git commit -m "chore: bump version to <NEXT_VERSION>"
   git tag -a "<NEXT_VERSION>" -m "<NEXT_VERSION>"
   ```
   `app/build.gradle.kts` declares `versionCode`/`versionName` as **plain literals**, and the
   script rewrites them (`v1.7.0` → `versionCode = 10700`, `versionName = "1.7.0"`). They are not
   derived from `git describe` because F-Droid finds an app's version by regex-scanning that file
   at each tag and cannot execute Gradle code — a computed version is invisible to it, every tag
   looks like the same version, and F-Droid would never offer an update.

   The consequence is the order above: the bump has to be **committed before the tag is created**,
   so the tag points at a commit whose `build.gradle.kts` already says the new version. This also
   means the release contains one commit that wasn't in the raw notes from step 3 — that's
   expected, and it's pure plumbing, so it earns no bullet in the announcement (see step 4).

   Note this commit shifts HEAD, so the "commit the release points at" shown to the user in step 7
   is this new commit, not the one that was HEAD when the run started.

   **The F-Droid changelog.** F-Droid shows a "What's New" note taken from
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` **in this repo at the tag**,
   so it has to be written and committed here, in this same commit — there is nowhere else to add
   it later without cutting another release. Write two files, named after the `VERSION_CODE` that
   `bump_version.sh` printed (`v1.7.0` → `10700.txt`):

   - `fastlane/metadata/android/ru-RU/changelogs/<VERSION_CODE>.txt`
   - `fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt`

   Both are the **same announcement from step 4**, cut down — ru-RU is it verbatim if it fits,
   en-US is it translated into English (the F-Droid listing is bilingual, so an English reader
   getting nothing is worse than a short note). Two differences from the GitHub release body:
   drop the "Full Changelog" compare-link footer, which is noise in an app store, and respect a
   hard **500-character limit** per file. Trim by dropping the least interesting bullets, not by
   compressing every bullet into something vague. Check both before committing:
   ```bash
   for f in fastlane/metadata/android/*/changelogs/<VERSION_CODE>.txt; do
     python3 -c "import sys; t=open(sys.argv[1],encoding='utf-8').read(); \
       print(sys.argv[1], len(t), 'chars', 'OK' if len(t) <= 500 else 'TOO LONG')" "$f"
   done
   ```
   (Count characters, not bytes — `wc -c` overcounts Cyrillic by a factor of two and will send
   you trimming text that already fits.)

   If this release has nothing user-visible in it (step 4 covers that case), still write both
   files, with the same one-line "internal improvements" note — F-Droid showing an empty What's
   New reads as a mistake.

   The tag is created locally but **not pushed**, keeping the publish-visible action gated behind
   the confirmation in step 7.

6. **Build the APK.**
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # only if not already set
   ./gradlew :app:assembleGithubRelease
   ```
   Use the **github** flavor's **release** build — `assembleRelease` on its own would build both
   flavors, and the `fdroid` one must never be published here: it has self-updating switched off
   (`BuildConfig.UPDATE_CHECK_ENABLED = false`) and no `REQUEST_INSTALL_PACKAGES` permission,
   because F-Droid's own client does the updating for apps installed from there. The APK lands at
   `app/build/outputs/apk/github/release/app-github-release.apk`. It is minified by R8 and signed with the project's release key,
   which `app/build.gradle.kts` reads from Gradle properties kept outside the repository
   (`NEWSRSSREADER_RELEASE_STORE_FILE` and friends, normally in `~/.gradle/gradle.properties`).
   The release build is roughly 1.3 MB against the debug build's ~18 MB, and janks about half
   as much on the same hardware, so it is what users should actually be given.

   **Verify the APK is really signed before going any further:**
   ```bash
   "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
     app/build/outputs/apk/github/release/app-github-release.apk
   ```
   It must print `CN=NewsRSSReader`. Two failure modes to watch for, both of which produce a
   build that *succeeds* rather than erroring:
   - The signing properties are missing (a fresh clone, a different machine, CI). The build
     then falls back to unsigned and emits `app-github-release-unsigned.apk` instead. An unsigned APK
     cannot be installed, so **stop** and tell the user the release key is not configured on
     this machine rather than publishing an uninstallable asset.
   - The certificate prints `CN=Android Debug`. That means the debug key is being used; stop
     and fix the configuration.

   Copy the output (`app/build/outputs/apk/github/release/app-github-release.apk`) to the scratchpad
   directory, renamed to include the version, e.g. `NewsRSSReader-<NEXT_VERSION>.apk`, since
   `gh release upload` uses the filename as the asset name and `app-github-release.apk` on its own
   isn't useful across multiple releases. Do this before asking for confirmation so the plan
   shown to the user is complete and doesn't stall mid-release on a build failure.

   **Signing key changes break updates in place.** Android refuses to install an APK over an
   installed copy signed with a different key. Releases `v1.0.0`–`v1.2.0` were signed with the
   debug key; everything from `v1.3.0` on uses the release key, so anyone still on one of those
   older builds has to uninstall before the new one will install, and the app's own updater
   cannot do it for them. If the signing key ever changes again, say so plainly in the release
   announcement — it is a user-visible consequence, not an implementation detail.

   If anything goes wrong here (or the user declines in the next step), roll back both the tag
   and the version-bump commit from step 5 before stopping:
   ```bash
   git tag -d "<NEXT_VERSION>"
   git reset --hard HEAD~1   # only if HEAD is still the bump commit
   ```
   (`reset --hard` also discards the changelog files, since they went into that same commit.)
   Neither was pushed, so this is a fully clean, invisible-to-everyone-else rollback. Check
   `git log -1 --oneline` first — if the user committed something else in the meantime, drop the
   bump with `git revert` instead of resetting over their work.

7. **Present the plan and get confirmation before touching anything remote.**
   Show the user: the new tag name, the commit it will point at (`git rev-parse --short
   HEAD`), the rewritten user-facing announcement from step 4 (not the raw commit list), and
   the built APK (path + size). Ask them to confirm before pushing the tag or creating the
   release — tagging and publishing a release are visible, hard-to-cleanly-undo actions
   (deleting a pushed tag/release after the fact is possible but messy if anyone already
   pulled it), so don't skip this even though the rest of the workflow is scripted. If they
   decline, roll back the tag and the bump commit as described at the end of step 6 and stop —
   nothing has touched the remote yet.

8. **On confirmation, push the bump commit and the tag, release, and attach the APK.**
   ```bash
   git push origin HEAD
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

9. **Report the result.** Once the app's `fdroiddata` metadata is merged upstream (it uses
   `UpdateCheckMode: Tags` + `AutoUpdateMode: Version`), mention that F-Droid picks the new tag
   up on its own, builds the `fdroid` flavor itself, and publishes it within 24–48 hours — no
   action needed here. Share the release URL that `gh release create` prints, the final
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
