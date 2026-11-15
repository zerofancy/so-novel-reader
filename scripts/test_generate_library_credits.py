import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('credits', Path(__file__).with_name('generate_library_credits.py'))
credits = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(credits)


class CreditExtractionTest(unittest.TestCase):
    def test_extracts_all_notices_not_copyright_icon_source(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'sample.jar'
            with zipfile.ZipFile(archive, 'w') as output:
                output.writestr('META-INF/LICENSE.txt', 'License body\n')
                output.writestr('META-INF/NOTICE', 'Attribution\n')
                output.writestr('src/Copyright.kt', '// Copyright 2026 Example\nclass Copyright')
            texts, holders = credits.archive_evidence(archive, source=True)
            self.assertEqual(len(texts), 2)
            self.assertIn('Copyright 2026 Example', holders)

    def test_historical_pom_without_namespace(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'sample.pom'
            pom.write_text('<project><organization><name>Real Author</name></organization>'
                           '<licenses><license><name>MIT</name><url>https://example.org/LICENSE</url>'
                           '</license></licenses></project>')
            metadata = credits.pom_metadata([pom])
            self.assertEqual(metadata['authors'], ['Real Author'])
            self.assertEqual(metadata['licenses'], [('MIT', 'https://example.org/LICENSE')])

    def test_retains_actual_source_license_declarations(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'sources.jar'
            declaration = '/* Copyright 2026 Alice. Permission is hereby granted. */'
            with zipfile.ZipFile(archive, 'w') as output:
                output.writestr('example/Actual.java', declaration + '\nclass Actual {}')
            texts, holders = credits.archive_evidence(archive, source=True)
            self.assertEqual(len(texts), 1)
            self.assertIn(declaration, texts[0][1])
            self.assertTrue(holders)

    def test_license_splitting_preserves_complete_text(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = credits.ASSETS
            credits.ASSETS = Path(directory)
            try:
                body = ''.join(f'line {index}\n' for index in range(1100))
                paths = credits.save_texts('sample', [('artifact!/LICENSE', body)])
                self.assertEqual(len(paths), 3)
                self.assertEqual(''.join((credits.ASSETS / p).read_text() for p in paths),
                                 '--- artifact!/LICENSE ---\n' + body)
                self.assertTrue(all(len((credits.ASSETS / p).read_text().splitlines()) <= 450 for p in paths))
            finally:
                credits.ASSETS = previous


if __name__ == '__main__':
    unittest.main()
