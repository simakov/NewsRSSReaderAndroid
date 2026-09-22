#!/usr/bin/env bash
# Rewrites the version literals in app/build.gradle.kts to match a vMAJOR.MINOR.PATCH tag.
#
# They have to be literals rather than something derived from `git describe`: F-Droid finds an
# app's version by regex-scanning this file at each tag and cannot execute Gradle code, so a
# computed version is invisible to it and no update would ever be offered. The cost is that the
# bump has to be committed *before* the tag is created, so the tag points at a commit whose
# build.gradle.kts already says the new version.
#
# Usage: bump_version.sh vMAJOR.MINOR.PATCH
# Prints VERSION_NAME=... and VERSION_CODE=... and leaves the edit unstaged.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 vMAJOR.MINOR.PATCH" >&2
  exit 2
fi

tag=$1
format='^v[0-9]+\.[0-9]+\.[0-9]+$'
if ! [[ $tag =~ $format ]]; then
  echo "error: '$tag' is not a vMAJOR.MINOR.PATCH tag" >&2
  exit 2
fi

version_name=${tag#v}
IFS='.' read -r major minor patch <<< "$version_name"
# Same encoding documented in app/build.gradle.kts and asserted by BuildConfigTest.
version_code=$((major * 10000 + minor * 100 + patch))

gradle_file="$(git rev-parse --show-toplevel)/app/build.gradle.kts"

# Anchored on the leading indentation so these can only match the defaultConfig assignments.
/usr/bin/sed -i '' \
  -e "s/^        versionCode = .*$/        versionCode = ${version_code}/" \
  -e "s/^        versionName = .*$/        versionName = \"${version_name}\"/" \
  "$gradle_file"

# The sed above silently does nothing if the lines ever move or change shape, which would ship a
# release built from the previous version's literals — so verify rather than trust.
if ! grep -q "^        versionCode = ${version_code}$" "$gradle_file" ||
   ! grep -q "^        versionName = \"${version_name}\"$" "$gradle_file"; then
  echo "error: failed to rewrite the version literals in $gradle_file" >&2
  echo "       check that defaultConfig still declares them at 8-space indentation" >&2
  exit 1
fi

echo "VERSION_NAME=${version_name}"
echo "VERSION_CODE=${version_code}"
