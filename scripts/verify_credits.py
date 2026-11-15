#!/usr/bin/env python3
"""Release preparation gate: inventory equality, offline assets and unresolved review."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'


def verify(inventory):
    errors = []
    artifacts = json.loads(inventory.read_text())['artifacts']
    expected = {a['coordinate'] for a in artifacts}
    entries = []
    for path in sorted((ASSETS / 'credits/libraries').glob('*.json')):
        entries.extend(json.loads(path.read_text()))
    actual = {e['id'] for e in entries}
    if actual != expected:
        errors.append(f'Runtime inventory mismatch: missing={sorted(expected - actual)}, stale={sorted(actual - expected)}')
    for directory in ('local',):
        for path in sorted((ASSETS / f'credits/{directory}').glob('*.json')):
            entries.extend(json.loads(path.read_text()))
    fonts = ASSETS / 'credits/fonts.json'
    if not fonts.is_file():
        errors.append('Font credits metadata missing')
    else:
        font_entries = json.loads(fonts.read_text())
        entries.extend(font_entries)
        if len(font_entries) != 3:
            errors.append('Expected exactly three bundled font credits')
    for entry in entries:
        for key in ('name', 'version', 'authors', 'copyright', 'license', 'projectUrl', 'licenseUrl'):
            if not entry.get(key) or '待核对' in entry[key]:
                errors.append(f'{entry["id"]}: unresolved {key}')
        if not entry.get('texts'):
            errors.append(f'{entry["id"]}: no offline license text')
        for relative in entry.get('texts', []):
            path = (ASSETS / relative).resolve()
            if not path.is_relative_to(ASSETS.resolve()):
                errors.append(f'{entry["id"]}: unsafe text path')
            elif not path.is_file() or not path.stat().st_size:
                # ptqflipper supplies its own module asset.
                module_path = ROOT / 'ptqflipper/src/main/assets' / relative
                if not module_path.is_file():
                    errors.append(f'{entry["id"]}: missing text {relative}')
    review = json.loads((ROOT / 'docs/credits/review.json').read_text())
    reviewed = {item['coordinate']: item for item in review}
    if set(reviewed) != expected:
        errors.append('Review inventory differs from the actual runtime graph')
    for artifact in artifacts:
        coordinate = artifact['coordinate']
        item = reviewed.get(coordinate, {})
        try:
            with Path(artifact['file']).open('rb') as source:
                digest = hashlib.file_digest(source, 'sha256').hexdigest()
            if digest != item.get('artifactSha256'):
                errors.append(f'{coordinate}: artifact bytes changed; re-review LICENSE/NOTICE and source provenance')
        except OSError as error:
            errors.append(f'{coordinate}: cannot verify artifact bytes: {error}')
    for item in review:
        if item['unresolved']:
            errors.append(f'{item["coordinate"]}: {"; ".join(item["unresolved"])}')
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        print(f'Credits NOT release-ready: {len(errors)} findings', file=sys.stderr)
        return 1
    print(f'Credits verified: {len(entries)} components; offline assets present')
    return 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    sys.exit(verify(parser.parse_args().inventory))
