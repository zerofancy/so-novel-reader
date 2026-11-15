#!/usr/bin/env python3
"""Fetch reviewed version-pinned upstream license evidence (no latest refs)."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
RULES = json.loads((ROOT / 'scripts/credit_license_sources.json').read_text())


def fetch(artifact):
    coordinate = artifact['coordinate']
    version = coordinate.split(':')[2]
    rule = next((r for r in RULES if coordinate.startswith(r['prefix'])), None)
    if not rule:
        return
    url = rule['url'].format(version=version)
    target = ROOT / 'build/credit-sources' / coordinate.replace(':', '_') / 'upstream-license.txt'
    try:
        if rule.get('local'):
            data = Path(rule['local']).read_bytes()
        else:
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read()
        text = data.decode('utf-8')
        if len(text) < 100 or '<html' in text.lower():
            raise ValueError('Invalid license response')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        evidence = dict(url=url, sha256=hashlib.sha256(data).hexdigest(),
                        license=rule['license'], authors=rule.get('authors', ''),
                        displayName=rule.get('displayName', ''),
                        copyrightStatement=rule.get('copyrightStatement', ''))
        if rule.get('noticeUrl'):
            notice_url = rule['noticeUrl'].format(version=version)
            with urllib.request.urlopen(notice_url, timeout=30) as response:
                notice = response.read().decode('utf-8')
            target.with_name('upstream-notice.txt').write_text(notice)
            evidence['noticeUrl'] = notice_url
        target.with_suffix('.json').write_text(json.dumps(evidence, indent=2) + '\n')
        print(coordinate, 'license downloaded')
    except (urllib.error.URLError, TimeoutError, ValueError) as error:
        print(coordinate, error)


if __name__ == '__main__':
    terms = ROOT / 'build/credit-sources/terms'
    terms.mkdir(parents=True, exist_ok=True)
    for name, url in (
        ('Apache-2.0', 'https://www.apache.org/licenses/LICENSE-2.0.txt'),
        ('GPL-2.0', 'https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt'),
    ):
        destination = terms / f'{name}.txt'
        if not destination.is_file():
            with urllib.request.urlopen(url, timeout=30) as response:
                destination.write_bytes(response.read())
    artifacts = json.loads(Path(sys.argv[1]).read_text())['artifacts']
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        list(executor.map(fetch, artifacts))
