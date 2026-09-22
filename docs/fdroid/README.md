# Publishing to F-Droid

F-Droid does not accept an APK. It builds the app from this repository itself, signs it with
F-Droid's own key, and publishes it — so everything it needs has to be reachable from a git tag.

Two things live here:

- [`com.newsrssreader.yml`](com.newsrssreader.yml) — the build recipe, the copy of record for
  what belongs in a fork of [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
- this runbook.

The listing text itself is **not** here: summary, description, screenshots, icon and per-version
changelogs live in [`fastlane/metadata/android/`](../../fastlane/metadata/android) at the repo
root, which F-Droid reads straight out of the tagged source. Editing them needs no merge request.

## What the code already does for F-Droid

- **The `fdroid` product flavor** is what F-Droid builds (`gradle: [fdroid]`). It has
  self-updating off and no `REQUEST_INSTALL_PACKAGES` permission, because the F-Droid client does
  the updating. See "Distribution channels" in [CLAUDE.md](../../CLAUDE.md).
- **`versionCode`/`versionName` are literals** in `app/build.gradle.kts`, bumped by the `release`
  skill in the commit each tag points at. F-Droid regex-scans that file and cannot run Gradle, so
  a computed version would be invisible to it.
- **No proprietary dependencies.** Every runtime dependency is open source (the AppMetrica
  analytics SDK was removed for exactly this reason), so there is nothing to strip at build time
  and no `scandelete`/`scanignore` in the recipe.
- **GPLv3**, declared in [`LICENSE`](../../LICENSE) and as `GPL-3.0-or-later` in the recipe.

## First submission, once

The recipe's build entry points at **`v1.7.0`**, which is the first tag that will contain the
`fdroid` flavor. Cut that release first (the `release` skill) — F-Droid cannot build `v1.6.0` or
anything older, since the flavor did not exist yet.

Then:

```bash
# 1. Fork https://gitlab.com/fdroid/fdroiddata, clone your fork, branch per app id
git checkout -b com.newsrssreader

# 2. Drop the recipe in
cp /path/to/NewsRSSReaderAndroid/docs/fdroid/com.newsrssreader.yml metadata/

# 3. Validate. rewritemeta settles field order and formatting, so expect it to reorder the file;
#    commit what it produces, not what you wrote.
fdroid lint com.newsrssreader
fdroid rewritemeta com.newsrssreader

# 4. Commit with the message convention their reviewers look for, and push
git commit -am "New App: com.newsrssreader"
git push -u origin com.newsrssreader
```

Then open a merge request against `fdroiddata`'s `master`. Optionally build it first — GitLab CI
runs on push to your fork, or `fdroid build -v -l com.newsrssreader` in their Docker image builds
it locally (~5 GB of disk).

Reviewers check the license on the files themselves, that no dependency is proprietary, that
nothing prebuilt or obfuscated is committed, and that the app builds in isolation. Expect
questions; answer them in the MR. After it merges, the first build appears in the repository
within **24–48 hours** (builds run in batches, and signing is deliberately manual).

## Every release after that: nothing

`UpdateCheckMode: Tags` + `AutoUpdateMode: Version` means F-Droid's bot watches this repo's tags,
reads the version literals out of `app/build.gradle.kts` at each new tag, writes the build entry
itself, builds and publishes. **No merge request per release.** Running the `release` skill —
which bumps the literals, writes the fastlane changelogs and pushes the tag — is the whole job.

What still needs a merge request is a change to the recipe itself: a new category, a renamed
flavor, a changed license, a new anti-feature. Change
[`com.newsrssreader.yml`](com.newsrssreader.yml) here in the same commit, so the two never drift.

## Things that would get the app pulled

- **Adding any closed-source dependency** — an analytics SDK, a crash reporter, a push service.
  This is the one that would end the F-Droid listing rather than just fail a build.
- **Renaming or removing the `fdroid` flavor** without updating the recipe: the build breaks on
  the next tag, silently, and the first sign is users not getting an update.
- **Changing how the version literals are declared** (moving them, computing them, reformatting
  the lines) — `bump_version.sh` verifies its own edit and `BuildConfigTest` guards the values,
  but F-Droid's regex scan is outside both.
