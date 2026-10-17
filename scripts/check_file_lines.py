#!/usr/bin/env python3
"""Check tracked and non-ignored new text files; optionally serve a Codex Stop hook."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LIMIT = 500
POLICY_NAME = '.codex/file-line-exceptions.json'


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'JSON 重复字段：{key}')
        result[key] = value
    return result


def load_exceptions(root, policy):
    exceptions = json.loads(policy.read_text(encoding='utf-8'), object_pairs_hook=unique_object)
    if not isinstance(exceptions, dict):
        raise ValueError('豁免登记必须是 JSON 对象')
    for name, entry in exceptions.items():
        path = PurePosixPath(name)
        if (not name or name == '.' or path.is_absolute() or '..' in path.parts
                or str(path) != name or any(char in name for char in '*?[]\\:\0')):
            raise ValueError(f'豁免必须使用精确的项目相对文件路径：{name!r}')
        if (root / name).is_dir():
            raise ValueError(f'禁止目录豁免：{name}')
        if (not isinstance(entry, dict) or set(entry) != {'max_lines', 'reason'}
                or type(entry['max_lines']) is not int or entry['max_lines'] <= LIMIT
                or not isinstance(entry['reason'], str) or not entry['reason'].strip()):
            raise ValueError(f'豁免须有大于 {LIMIT} 的整数 max_lines 和非空 reason：{name}')
    return exceptions


def git_output(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, timeout=10, stderr=subprocess.PIPE)


def file_names(root):
    # Read index modes so an initialized submodule is never traversed.
    entries = git_output(root, 'ls-files', '--stage', '-z').split(b'\0')
    names, submodules = set(), set()
    for entry in filter(None, entries):
        metadata, raw_name = entry.split(b'\t', 1)
        name = os.fsdecode(raw_name)
        if metadata.split(b' ', 1)[0] == b'160000':
            submodules.add(name)
        else:
            names.add(name)
    new = git_output(root, 'ls-files', '--others', '--exclude-standard', '-z')
    names.update(os.fsdecode(name) for name in new.split(b'\0') if name)
    for name in sorted(names):
        parts = PurePosixPath(name).parts
        ancestors = [PurePosixPath(*parts[:i]).as_posix() for i in range(1, len(parts) + 1)]
        if any(parent in submodules or (root / parent).is_symlink() for parent in ancestors):
            continue
        yield name


def check(root=ROOT, policy=None):
    root = Path(root)
    exceptions = load_exceptions(root, Path(policy) if policy else root / POLICY_NAME)
    errors = []
    for name in file_names(root):
        path = root / name
        # Deleted files and directories have no local text to check.
        if not path.is_file():
            continue
        data = path.read_bytes()
        if b'\0' in data:
            continue
        try:
            text = data.decode('utf-8')
        except UnicodeDecodeError:
            continue
        # Count physical lines, including blanks/comments and an unterminated last line.
        count = text.count('\n') + int(bool(text) and not text.endswith('\n'))
        limit = exceptions.get(name, {}).get('max_lines', LIMIT)
        if count > limit:
            errors.append(f'{name}: {count} 行，上限 {limit}')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--hook', action='store_true', help='Codex Stop hook JSON protocol')
    args = parser.parse_args()
    try:
        if args.hook:
            payload = json.load(sys.stdin)
            if not isinstance(payload, dict) or payload.get('hook_event_name') != 'Stop':
                raise ValueError('预期 Codex Stop hook 输入')
        errors = check()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        errors = [f'行数检查无法完成：{error}']
    reason = ('项目文件行数检查失败：\n' + '\n'.join(errors)
              + '\n请按职责拆分文件。确需豁免时，在 .codex/file-line-exceptions.json '
                '逐文件记录原因和上限；不要压缩代码、删除必要说明或修改忽略规则绕过检查。')
    if args.hook:
        print(json.dumps({'decision': 'block', 'reason': reason} if errors else {}, ensure_ascii=False))
        return 0
    print(reason if errors else '文件行数检查通过（默认上限 500 行）。',
          file=sys.stderr if errors else sys.stdout)
    return int(bool(errors))


if __name__ == '__main__':
    sys.exit(main())
