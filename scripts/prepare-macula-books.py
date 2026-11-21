#!/usr/bin/env python3
"""Bundle lowfat XML and extract words in canonical reading order."""
import json
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def prepare(xml_dir, output, token_file):
    seen = set()
    books = set()
    tokens = []
    for path in sorted(xml_dir.glob("*.xml")):
        tree = ET.parse(path)
        root = tree.getroot()
        book = root.get("id")
        if root.tag != "book" or not book or book in books:
            raise ValueError(f"Invalid or duplicate book in {path}")
        books.add(book)
        for word in root.iter("w"):
            ref = word.get("ref", "")
            match = re.fullmatch(r"(\S+) (\d+):(\d+)!(\d+)", ref)
            if not match or match[1] != book or ref in seen:
                raise ValueError(f"Invalid or duplicate XML reference: {ref}")
            seen.add(ref)
            lemma = word.get("lemma", "")
            if not lemma:
                raise ValueError(f"Missing lemma: {ref}")
            tokens.append((
                (len(books), *(int(match[i]) for i in (2, 3, 4))),
                {
                    "surface": word.get("text", (word.text or "").strip()),
                    "lemma": lemma,
                    "pos": word.get("class", ""),
                    "type": word.get("type", ""),
                    "verse": ref.split("!", 1)[0],
                    "gloss": word.get("english") or word.get("gloss", ""),
                },
            ))
        shutil.copyfile(path, output / f"{book}.xml")
    if not tokens:
        raise ValueError("No MACULA words found")
    token_file.write_text(json.dumps([token for _, token in sorted(tokens)], ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    prepare(*(Path(arg) for arg in sys.argv[1:]))
