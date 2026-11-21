import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class PrepareBooksTest(unittest.TestCase):
    def test_extracts_words_in_reference_order_and_copies_xml(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            xml_dir = root / "xml"
            output = root / "books"
            xml_dir.mkdir()
            output.mkdir()
            book = xml_dir / "john.xml"
            xml = ('<book id="JHN"><wg class="cl">'
                   '<w ref="JHN 1:1!2" lemma="second" class="verb" english="say">says</w>'
                   '<w ref="JHN 1:1!1" lemma="first" class="noun" type="proper" gloss="name">Name</w>'
                   '</wg></book>')
            book.write_text(xml, encoding="utf-8")
            tokens = root / "tokens.json"
            command = [sys.executable, str(Path(__file__).with_name("prepare-macula-books.py")),
                       str(xml_dir), str(output), str(tokens)]
            subprocess.run(command, check=True)
            self.assertEqual(xml, (output / "JHN.xml").read_text(encoding="utf-8"))
            self.assertEqual([
                {"surface": "Name", "lemma": "first", "pos": "noun", "type": "proper",
                 "verse": "JHN 1:1", "gloss": "name"},
                {"surface": "says", "lemma": "second", "pos": "verb", "type": "",
                 "verse": "JHN 1:1", "gloss": "say"},
            ], json.loads(tokens.read_text(encoding="utf-8")))
            book.write_text('<book id="JHN"><w ref="JHN 1:1!1" lemma="x"/>'
                            '<w ref="JHN 1:1!1" lemma="y"/></book>', encoding="utf-8")
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("duplicate XML reference", result.stderr)


if __name__ == "__main__":
    unittest.main()
