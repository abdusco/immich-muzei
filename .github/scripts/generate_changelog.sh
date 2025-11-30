#!/usr/bin/env bash
set -euo pipefail

# Generate a changelog using only commits with "feat:" and "fix:" prefixes.
# Outputs to stdout.

TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
if [ -n "$TAG" ]; then
  RANGE="$TAG..HEAD"
  HEADER="Changelog since $TAG"
else
  RANGE=""
  HEADER="Changelog (no tags found — showing all matching commits)"
fi

echo "$HEADER"

echo
echo "## Features"
FEATS=$(git log $RANGE --grep='^feat:' --pretty=format:"- %s (%h)" || true)
if [ -z "$FEATS" ]; then
  echo "- None"
else
  # Remove the "feat: " prefix from the subject for cleaner output
  echo "$FEATS" | sed 's/- feat: /- /'
fi

echo
echo "## Fixes"
FIXES=$(git log $RANGE --grep='^fix:' --pretty=format:"- %s (%h)" || true)
if [ -z "$FIXES" ]; then
  echo "- None"
else
  # Remove the "fix: " prefix from the subject for cleaner output
  echo "$FIXES" | sed 's/- fix: /- /'
fi

