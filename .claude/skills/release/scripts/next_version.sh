#!/usr/bin/env bash
# Computes the next release version by bumping MINOR (+1) and resetting PATCH to 0.
# Prints two lines:
#   LAST_TAG=<last vX.Y.Z tag, or empty if none exist>
#   NEXT_VERSION=<next vX.Y.0 tag>
set -euo pipefail

git fetch --tags --quiet

last_tag=$(git tag -l 'v[0-9]*.[0-9]*.[0-9]*' | sort -V | tail -1)

if [ -z "$last_tag" ]; then
  echo "LAST_TAG="
  echo "NEXT_VERSION=v0.1.0"
  exit 0
fi

ver=${last_tag#v}
IFS='.' read -r major minor _patch <<< "$ver"
next_minor=$((minor + 1))

echo "LAST_TAG=${last_tag}"
echo "NEXT_VERSION=v${major}.${next_minor}.0"
