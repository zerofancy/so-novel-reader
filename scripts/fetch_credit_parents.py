#!/usr/bin/env python3
"""Save inherited POM metadata for actual version-pinned release artifacts."""
import json
from pathlib import Path
import sys
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'build/credit-sources/poms'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def parents(path, seen):
    root = ET.parse(path).getroot()
    parent = root.find('m:parent', NS)
    if parent is None:
        return
    group = parent.findtext('m:groupId', '', NS)
    name = parent.findtext('m:artifactId', '', NS)
    version = parent.findtext('m:version', '', NS)
    coordinate = f'{group}:{name}:{version}'
    if coordinate in seen or '${' in coordinate:
        return
    seen.add(coordinate)
    destination = DEST / group / name / version / f'{name}-{version}.pom'
    if not destination.is_file():
        url = f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{name}/{version}/{name}-{version}.pom'
        try:
            with urllib.request.urlopen(url, timeout=20) as response:
                data = response.read()
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
        except Exception as error:
            print(coordinate, error)
            return
    parents(destination, seen)


if __name__ == '__main__':
    seen = set()
    for pom in json.loads(Path(sys.argv[1]).read_text())['poms']:
        for file in pom['files']:
            parents(Path(file), seen)
    print(f'{len(seen)} inherited POM coordinates inspected')
