"""Boundary and subprocess contract tests using disposable Git repositories."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

# Do not leave untracked bytecode files in the repository being checked.
sys.dont_write_bytecode = True
SCRIPT = Path(__file__).with_name('check_file_lines.py')
spec = importlib.util.spec_from_file_location('check_file_lines', SCRIPT)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class FileLinesTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.git('init', '-q')
        self.policy = self.write('.codex/file-line-exceptions.json', '{}')

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.root, check=True,
                              text=True, capture_output=True)

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        return path

    def check(self):
        return checker.check(self.root)

    def policy_for(self, name, limit=502, reason='不可拆分的测试数据'):
        self.policy.write_text(json.dumps({name: {'max_lines': limit, 'reason': reason}}))

    def install_script(self):
        script = self.root / 'scripts/check_file_lines.py'
        script.parent.mkdir(exist_ok=True)
        script.write_bytes(SCRIPT.read_bytes())
        return script

    def run_script(self, hook=False, payload=None):
        return subprocess.run(
            [sys.executable, str(self.install_script()), *(['--hook'] if hook else [])],
            cwd=self.root.parent, input=payload, text=True, capture_output=True,
        )

    def assert_block(self, result, expected):
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('', result.stderr)
        output = json.loads(result.stdout)
        self.assertEqual({'decision', 'reason'}, set(output))
        self.assertEqual('block', output['decision'])
        self.assertIn(expected, output['reason'])

    def test_500_and_501_with_and_without_final_newline(self):
        for final in (True, False):
            for count in (500, 501):
                with self.subTest(final=final, count=count):
                    self.write('boundary.kt', '\n' * count if final else '\n' * (count - 1) + 'last')
                    self.assertEqual([] if count == 500 else ['boundary.kt: 501 行，上限 500'], self.check())

    def test_blank_comments_empty_and_crlf(self):
        self.write('empty', '')
        self.write('comments.kt', '// comment\n' * 500)
        self.assertEqual([], self.check())
        (self.root / 'crlf.kt').write_bytes(b'\r\n' * 501)
        self.assertEqual(['crlf.kt: 501 行，上限 500'], self.check())

    def test_new_ignored_tracked_ignored_and_deleted(self):
        self.write('.gitignore', '*.ignored\n')
        self.write('new.ignored', '\n' * 501)
        self.write('tracked.ignored', '\n' * 501)
        deleted = self.write('deleted.kt', '\n' * 501)
        self.git('add', '-f', 'tracked.ignored', 'deleted.kt')
        deleted.unlink()
        self.write('new.kt', '\n' * 501)
        self.assertEqual(['new.kt: 501 行，上限 500', 'tracked.ignored: 501 行，上限 500'], self.check())

    def test_binary_symlinks_and_replaced_parent_directory(self):
        self.write('.gitignore', '*.ignored\n')
        target = self.write('large.ignored', '\n' * 501)
        (self.root / 'link.kt').symlink_to(target)
        (self.root / 'dangling.kt').symlink_to('missing')
        self.write('folder/tracked.kt', 'old\n')
        self.git('add', 'folder/tracked.kt')
        (self.root / 'folder/tracked.kt').unlink()
        (self.root / 'folder').rmdir()
        outside = tempfile.TemporaryDirectory()
        self.addCleanup(outside.cleanup)
        Path(outside.name, 'tracked.kt').write_text('\n' * 501)
        (self.root / 'folder').symlink_to(outside.name, target_is_directory=True)
        (self.root / 'nul.bin').write_bytes(b'\0' + b'\n' * 501)
        (self.root / 'non-utf8.bin').write_bytes(b'\xff' + b'\n' * 501)
        self.assertEqual([], self.check())

    def test_initialized_submodule_directory_is_skipped(self):
        nested = self.root / 'module'
        nested.mkdir()
        subprocess.run(['git', 'init', '-q', str(nested)], check=True)
        (nested / 'large.kt').write_text('\n' * 501)
        subprocess.run(['git', 'add', '.'], cwd=nested, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.test',
                        'commit', '-qm', 'fixture'], cwd=nested, check=True)
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=nested, text=True).strip()
        self.git('update-index', '--add', '--cacheinfo', f'160000,{commit},module')
        self.assertEqual([], self.check())

    def test_exact_exception_limit(self):
        self.policy_for('large.kt')
        self.write('large.kt', '\n' * 502)
        self.assertEqual([], self.check())
        self.write('large.kt', '\n' * 503)
        self.write('other/large.kt', '\n' * 501)
        self.assertEqual(['large.kt: 503 行，上限 502', 'other/large.kt: 501 行，上限 500'], self.check())

    def test_invalid_entry(self):
        for entry in ({}, {'max_lines': 600}, {'max_lines': True, 'reason': 'x'},
                      {'max_lines': 500, 'reason': 'x'}, {'max_lines': -1, 'reason': 'x'},
                      {'max_lines': 501.5, 'reason': 'x'}, {'max_lines': '600', 'reason': 'x'},
                      {'max_lines': 600, 'reason': '  '}, {'max_lines': 600, 'reason': None},
                      {'max_lines': 600, 'reason': 'x', 'typo': 1}, []):
            with self.subTest(entry=entry):
                self.policy.write_text(json.dumps({'file.kt': entry}))
                with self.assertRaises(ValueError):
                    self.check()

    def test_invalid_path_and_directory(self):
        (self.root / 'folder').mkdir()
        for name in ('', '.', '/absolute.kt', '../escape.kt', 'a/../b.kt', './a.kt',
                     'a//b.kt', 'folder/', 'folder', '*.kt', 'a?.kt', '[ab].kt',
                     'a\\b.kt', 'C:/file.kt', 'nul\0.kt'):
            with self.subTest(name=name):
                self.policy_for(name)
                with self.assertRaises(ValueError):
                    self.check()

    def test_invalid_json_root_duplicates_and_missing_config(self):
        for content in ('[', '[]', 'null', '1', '{"a": {}, "a": {}}',
                        '{"a": {"reason": "x", "reason": "y", "max_lines": 600}}'):
            with self.subTest(content=content):
                self.policy.write_text(content)
                with self.assertRaises(ValueError):
                    self.check()
        self.policy.unlink()
        with self.assertRaises(OSError):
            self.check()

    def test_cli_status_and_root_independence(self):
        passed = self.run_script()
        self.assertEqual(0, passed.returncode, passed.stderr)
        self.write('long.kt', '\n' * 501)
        failed = self.run_script()
        self.assertEqual(1, failed.returncode)
        self.assertIn('long.kt: 501 行，上限 500', failed.stderr)
        self.assertEqual('', failed.stdout)

    def test_hook_success_failure_and_repeated_stop(self):
        success = self.run_script(True, json.dumps({'hook_event_name': 'Stop'}))
        self.assertEqual(0, success.returncode, success.stderr)
        self.assertEqual({}, json.loads(success.stdout))
        for active in (False, True):
            self.policy_for('long.kt')
            self.write('long.kt', '\n' * 503)
            result = self.run_script(True, json.dumps({'hook_event_name': 'Stop', 'stop_hook_active': active}))
            self.assert_block(result, 'long.kt: 503 行，上限 502')

    def test_hook_invalid_input_config_and_git_error_block(self):
        for payload in ('bad json', '[]', '{}', '{"hook_event_name":"PostToolUse"}'):
            self.assert_block(self.run_script(True, payload), '行数检查无法完成')
        stop = json.dumps({'hook_event_name': 'Stop'})
        self.policy.write_text('[]')
        self.assert_block(self.run_script(True, stop), '豁免登记必须是 JSON 对象')
        self.policy.write_text('{}')
        (self.root / '.git').rename(self.root / 'saved-git')
        self.assert_block(self.run_script(True, stop), '行数检查无法完成')

    def test_configured_hook_command_from_subdirectory(self):
        config = json.loads((SCRIPT.parent.parent / '.codex/hooks.json').read_text())
        hook = config['hooks']['Stop'][0]['hooks'][0]
        self.assertEqual('command', hook['type'])
        self.assertGreater(hook['timeout'], 0)
        self.install_script()
        self.write('long.kt', '\n' * 501)
        result = subprocess.run(hook['command'], shell=True, cwd=self.root / 'scripts',
                                input='{"hook_event_name":"Stop"}', text=True, capture_output=True)
        self.assert_block(result, 'long.kt: 501 行，上限 500')


if __name__ == '__main__':
    unittest.main()
