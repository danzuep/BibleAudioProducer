#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
output="$1"
source_dir="$root/app/build/macula-source"
macula_revision=dcfd3c024b3a00b51fcabc7e154129958500c1f9
greek_revision=8423afe47b9e8f24b7772e808af45c7159a6fe7e

mkdir -p "$source_dir/site/static" "$output"
for file in LICENSE site/static/recording-prompts.js; do
  curl --fail --location --retry 3 --connect-timeout 15 --max-time 180 --silent --show-error \
    "https://raw.githubusercontent.com/danzuep/MaculaScripta/$macula_revision/$file" \
    --output "$source_dir/$file"
done
cp "$source_dir/LICENSE" "$output/MaculaScripta-LICENSE.txt"
curl --fail --location --retry 3 --connect-timeout 15 --max-time 180 --silent --show-error \
  "https://raw.githubusercontent.com/Clear-Bible/macula-greek/$greek_revision/SBLGNT/lowfat/sblgntlowfat.xml" \
  --output "$source_dir/lowfat-index.xml"
mkdir -p "$source_dir/lowfat" "$output/books"
python3 - "$source_dir/lowfat-index.xml" <<'PY' | while IFS= read -r file; do
import sys
import xml.etree.ElementTree as ET
for child in ET.parse(sys.argv[1]).getroot():
    print(child.attrib["href"])
PY
  curl --fail --location --retry 3 --connect-timeout 15 --max-time 180 --silent --show-error \
    "https://raw.githubusercontent.com/Clear-Bible/macula-greek/$greek_revision/SBLGNT/lowfat/$file" \
    --output "$source_dir/lowfat/$file"
done
python3 "$root/scripts/prepare-macula-books.py" \
  "$source_dir/lowfat" "$output/books" "$source_dir/tokens.json"

printf '%s\n' '{"type":"module"}' > "$source_dir/package.json"
node "$root/scripts/generate-recording-plan.mjs" \
  "$source_dir/tokens.json" "$source_dir/site/static/recording-prompts.js" "$output/recording-plan.json"
