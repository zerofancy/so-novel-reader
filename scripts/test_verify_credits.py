import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import verify_credits


class CreditsGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / 'app/src/main/assets'
        (self.assets / 'credits/libraries').mkdir(parents=True)
        (self.root / 'docs/credits').mkdir(parents=True)
        (self.assets / 'license.txt').write_text('Actual license and copyright')
        self.artifact = self.root / 'dependency.jar'
        self.artifact.write_bytes(b'original bytecode')
        self.inventory = self.root / 'inventory.json'
        self.inventory.write_text(json.dumps({'artifacts': [
            {'coordinate': 'example:library:1.0-SNAPSHOT', 'file': str(self.artifact)},
        ]}))
        entry = {'id': 'example:library:1.0-SNAPSHOT', 'name': 'Library', 'version': '1.0-SNAPSHOT',
                 'authors': 'Author', 'copyright': 'Copyright Author', 'license': 'MIT',
                 'projectUrl': 'https://example.org', 'licenseUrl': 'https://example.org/LICENSE',
                 'texts': ['license.txt']}
        (self.assets / 'credits/libraries/library.json').write_text(json.dumps([entry]))
        (self.assets / 'credits/fonts.json').write_text(json.dumps([
            dict(entry, id=f'font:{i}') for i in range(3)
        ]))
        self.review = self.root / 'docs/credits/review.json'
        self.review.write_text(json.dumps([{'coordinate': entry['id'], 'unresolved': [],
            'artifactSha256': hashlib.sha256(self.artifact.read_bytes()).hexdigest()}]))

    def gate(self):
        with patch.object(verify_credits, 'ROOT', self.root), patch.object(verify_credits, 'ASSETS', self.assets), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return verify_credits.verify(self.inventory)

    def test_accepts_exact_reviewed_artifact(self):
        self.assertEqual(0, self.gate())

    def test_rejects_changed_snapshot_with_same_coordinate(self):
        self.artifact.write_bytes(b'different bytecode, same version')
        self.assertEqual(1, self.gate())

    def test_rejects_missing_offline_text(self):
        (self.assets / 'license.txt').unlink()
        self.assertEqual(1, self.gate())

    def test_rejects_missing_review(self):
        self.review.write_text('[]')
        self.assertEqual(1, self.gate())


if __name__ == '__main__':
    unittest.main()
