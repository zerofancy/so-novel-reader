import unittest

from verify_font_resources import ASSETS, verify_license_content


class FontLicenseContentTest(unittest.TestCase):
    def setUp(self):
        self.text = (ASSETS / 'fonts/licenses/zhuque.txt').read_text(encoding='utf-8')

    def test_accepts_original_content(self):
        verify_license_content(self.text)

    def test_accepts_changed_line_endings_and_trailing_spaces(self):
        converted = '\r\n'.join(line.rstrip() + '  ' for line in self.text.splitlines())
        self.assertNotEqual(self.text.encode(), converted.encode())
        verify_license_content(converted)

    def test_rejects_missing_terms(self):
        with self.assertRaises(AssertionError):
            verify_license_content(self.text.replace('DISCLAIMER', ''))

    def test_rejects_missing_copyright(self):
        with self.assertRaises(AssertionError):
            verify_license_content(self.text.replace('Copyright', '').replace('©', ''))


if __name__ == '__main__':
    unittest.main()
