#!/usr/bin/env bash
# Builds a Berean Standard Bible zip containing only John 1-2, as an English reference for the word-order tests.
# Usage: make-minimal-bsb.sh <full engbsb_usfm.zip> <output zip>
# Get the full zip from https://ebible.org/Scriptures/engbsb_usfm.zip
set -euo pipefail

src="$1"
out="$(realpath -m "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

unzip -q "$src" -d "$work/full"
john="$(find "$work/full" -type f \( -name '*.usfm' -o -name '*.sfm' \) | grep -E 'JHN|-43-|73-' | head -n 1)"
if [ -z "$john" ]; then
  echo "John USFM file not found in $src" >&2
  exit 1
fi

mkdir -p "$work/minimal"
# Keep the file header and chapters 1 and 2, dropping everything from chapter 3 onward.
awk '/^\\c 3([[:space:]]|$)/ { exit } { print }' "$john" > "$work/minimal/$(basename "$john")"
grep -q '^\\c 1' "$work/minimal/$(basename "$john")"

copr="$(find "$work/full" -type f -iname 'copr.htm' | head -n 1)"
if [ -n "$copr" ]; then cp "$copr" "$work/minimal/copr.htm"; fi

(cd "$work/minimal" && zip -q -r "$out" .)
echo "Wrote $out ($(unzip -l "$out" | tail -n 1))"
