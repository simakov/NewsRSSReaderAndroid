#!/usr/bin/env bash
# Builds GitHub release notes (markdown) grouped by Conventional Commit type,
# from $1 (last tag, may be empty) to HEAD.
#
# Usage: build_notes.sh <last_tag_or_empty> <next_version>
#
# Written for bash 3.2 (macOS default) - no associative arrays.
set -euo pipefail

last_tag="${1:-}"
next_version="${2:?next_version required}"

if [ -n "$last_tag" ]; then
  range="${last_tag}..HEAD"
else
  range="HEAD"
fi

# type:title pairs, in display order. "other" is handled separately at the end.
group_defs=(
  "feat:Features"
  "fix:Fixes"
  "perf:Performance"
  "refactor:Refactoring"
  "docs:Documentation"
  "test:Tests"
  "build:Build"
  "ci:CI"
  "style:Style"
  "chore:Chores"
)

title_for_type() {
  local t="$1"
  for def in "${group_defs[@]}"; do
    if [ "${def%%:*}" = "$t" ]; then
      echo "${def#*:}"
      return
    fi
  done
  echo ""
}

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
for def in "${group_defs[@]}"; do
  : > "$tmpdir/${def%%:*}"
done
: > "$tmpdir/other"

conventional_commit_re="^([a-zA-Z]+)(\([^)]*\))?!?:\ (.*)$"

while IFS= read -r line || [ -n "$line" ]; do
  [ -z "$line" ] && continue
  hash=${line%% *}
  subject=${line#* }
  type="other"
  bucketed_subject="$subject"
  if [[ "$subject" =~ $conventional_commit_re ]]; then
    raw_type=$(echo "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')
    rest="${BASH_REMATCH[3]}"
    if [ -n "$(title_for_type "$raw_type")" ]; then
      type="$raw_type"
      bucketed_subject="$rest"
    fi
  fi
  echo "- ${bucketed_subject} (${hash})" >> "$tmpdir/$type"
done < <(git log "$range" --pretty=format:'%h %s' --no-merges)

echo "## What's Changed"
echo

any_commits=false
for def in "${group_defs[@]}" "other:Other"; do
  t="${def%%:*}"
  title="${def#*:}"
  if [ -s "$tmpdir/$t" ]; then
    any_commits=true
    echo "### ${title}"
    cat "$tmpdir/$t"
    echo
  fi
done

if [ "$any_commits" = false ]; then
  echo "_No commits since the previous release._"
  echo
fi

remote_url=$(git remote get-url origin 2>/dev/null || true)
repo_slug=$(echo "$remote_url" | sed -E 's#(git@|https://)github\.com[:/]([^/]+/[^/.]+)(\.git)?#\2#')

if [ -n "$last_tag" ] && [ -n "$repo_slug" ]; then
  echo "**Full Changelog**: https://github.com/${repo_slug}/compare/${last_tag}...${next_version}"
fi
