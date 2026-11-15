#!/usr/bin/env python3
"""Fetch exact-version source evidence to a workspace cache; never fetch latest."""
import concurrent.futures
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'build/credit-sources'


def fetch(artifact):
    coordinate = artifact['coordinate']
    group, name, version = coordinate.split(':')
    if group == 'io.github.zerofancy':
        return coordinate, 'local Maven sources inspected separately'
    base = ('https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.')
            else 'https://repo.maven.apache.org/maven2/')
    url = base + f'{group.replace(".", "/")}/{name}/{version}/{name}-{version}-sources.jar'
    target = DESTINATION / coordinate.replace(':', '_') / f'{name}-{version}-sources.jar'
    if target.is_file():
        return coordinate, 'cached'
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            data = response.read()
        if not data.startswith(b'PK'):
            return coordinate, 'not a ZIP archive'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        target.with_suffix('.url').write_text(url + '\n')
        return coordinate, 'downloaded'
    except (urllib.error.URLError, TimeoutError) as error:
        return coordinate, str(error)


if __name__ == '__main__':
    artifacts = json.loads(Path(sys.argv[1]).read_text())['artifacts']
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        for coordinate, status in executor.map(fetch, artifacts):
            print(coordinate, status)
