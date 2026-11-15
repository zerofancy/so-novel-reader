#!/usr/bin/env python3
"""Extract version-specific bundled licenses, POM metadata and source copyrights.

Run after :app:exportReleaseCreditsInventory. Never downloads a generic license.
Missing evidence remains explicitly unresolved; review report before release.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def pom_metadata(paths):
    result = {'authors': [], 'licenses': [], 'url': ''}
    queue = list(paths)
    visited = set()
    while queue:
        path = Path(queue.pop(0))
        if path in visited:
            continue
        visited.add(path)
        root = ET.parse(path).getroot()
        # Historical POMs (kxml2) do not declare the Maven XML namespace.
        if not root.tag.startswith('{'):
            for node in root.iter():
                node.tag = '{' + NS['m'] + '}' + node.tag
        parent = root.find('m:parent', NS)
        if parent is not None:
            group = parent.findtext('m:groupId', '', NS)
            name = parent.findtext('m:artifactId', '', NS)
            version = parent.findtext('m:version', '', NS)
            queue.extend((CACHE / group / name / version).glob('*/*.pom'))
            queue.extend((ROOT / 'build/credit-sources/poms' / group / name / version).glob('*.pom'))
        def values(query):
            return [e.text.strip() for e in root.findall(query, NS) if e.text]
        result['authors'] += values('m:developers/m:developer/m:name')
        result['authors'] += values('m:organization/m:name')
        result['licenses'] += [(e.findtext('m:name', '', NS), e.findtext('m:url', '', NS))
                               for e in root.findall('m:licenses/m:license', NS)]
        result['url'] = result['url'] or root.findtext('m:url', '', NS)
    return result


def archive_evidence(path, source=False):
    texts, copyrights = [], set()
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            leaf = name.rsplit('/', 1)[-1].lower()
            if re.match(r'^(license|licence|notice|copying|copyright)([._-].*)?$', leaf) and not leaf.endswith(('.kt', '.java', '.class')):
                try:
                    text = archive.read(name).decode('utf-8')
                    if '\0' not in text:
                        texts.append((f'{path.name}!/{name}', text))
                except UnicodeDecodeError:
                    pass
            if source and name.endswith(('.kt', '.java')):
                header = archive.read(name).decode('utf-8', errors='replace')[:4000]
                for block in re.findall(r'/\*.*?\*/', header, re.S):
                    if re.search(r'copyright|licensed under|permission is hereby', block, re.I):
                        texts.append((f'{path.name}!/{name} (original source declaration)', block + '\n'))
                for line in header.splitlines()[:65]:
                    if re.search(r'copyright\s+(?:\(c\)\s*)?(?:19|20)\d{2}', line, re.I):
                        copyrights.add(line.strip(' /*\t'))
            if name == 'classes.jar':
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                    for inner in sorted(nested.namelist()):
                        if re.match(r'^(license|notice|copying)([._-].*)?$', inner.rsplit('/', 1)[-1], re.I):
                            texts.append((f'{path.name}!/{name}!/{inner}', nested.read(inner).decode('utf-8')))
    return texts, copyrights


def has_classes(path):
    with zipfile.ZipFile(path) as archive:
        if any(name.endswith('.class') for name in archive.namelist()):
            return True
        if 'classes.jar' in archive.namelist():
            with zipfile.ZipFile(io.BytesIO(archive.read('classes.jar'))) as nested:
                return any(name.endswith('.class') for name in nested.namelist())
    return False


def save_texts(slug, texts):
    paths = []
    # Deduplicate identical license payloads, but retain distinct notices.
    seen = set()
    for origin, text in texts:
        digest = hashlib.sha256(text.encode()).hexdigest()
        if digest in seen:
            continue
        seen.add(digest)
        payload = f'--- {origin} ---\n{text}'
        if not payload.endswith('\n'):
            payload += '\n'
        lines = payload.splitlines(keepends=True)
        for start in range(0, len(lines), 450):
            relative = f'credits/texts/{slug}-{len(paths):02d}.txt'
            path = ASSETS / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(''.join(lines[start:start + 450]))
            paths.append(relative)
    return paths


def generate(inventory):
    data = json.loads(inventory.read_text())
    # These directories are generator-owned; font/local metadata lives elsewhere.
    for folder in ('credits/libraries', 'credits/texts'):
        directory = (ASSETS / folder).resolve()
        assert directory.parent == (ASSETS / 'credits').resolve()
        for old in directory.glob('*'):
            assert old.resolve().parent == directory
            if old.is_file():
                old.unlink()
    artifacts = {a['coordinate']: a for a in data['artifacts'] if a['coordinate'].count(':') == 2}
    pom_paths = {p['coordinate']: p['files'] for p in data.get('poms', [])}
    report = []
    for coordinate, artifact in sorted(artifacts.items()):
        group, artifact_id, version = coordinate.split(':')
        if group == 'so-novel-reader' or artifact_id == 'ptqflipper':
            continue
        directory = CACHE / group / artifact_id / version
        local_directory = Path.home() / '.m2/repository' / group.replace('.', '/') / artifact_id / version
        poms = (pom_paths.get(coordinate) or list(directory.glob('*/*.pom'))
                or list(local_directory.glob('*.pom')))
        meta = pom_metadata(poms)
        path = Path(artifact['file'])
        texts, copyrights = archive_evidence(path)
        downloaded = ROOT / 'build/credit-sources' / coordinate.replace(':', '_')
        sources = (list(directory.glob('*/*-sources.jar')) + list(local_directory.glob('*-sources.jar'))
                   + list(downloaded.glob('*-sources.jar')))
        for source in sorted(sources):
            extra, holders = archive_evidence(source, source=True)
            texts += extra
            copyrights |= holders
        supplemental_override = {}
        supplemental = downloaded / 'upstream-license.txt'
        supplemental_metadata = downloaded / 'upstream-license.json'
        if supplemental.is_file() and supplemental_metadata.is_file():
            evidence = json.loads(supplemental_metadata.read_text())
            texts.append((evidence['url'], supplemental.read_text()))
            meta['licenses'].append((evidence['license'], evidence['url']))
            supplemental_override = evidence
            if evidence.get('noticeUrl'):
                texts.append((evidence['noticeUrl'], (downloaded / 'upstream-notice.txt').read_text()))
            if evidence.get('authors'):
                meta['authors'].append(evidence['authors'])
        for _, text in texts:
            for line in text.splitlines():
                if re.search(r'copyright\s+(?:\(c\)\s*)?(?:19|20)\d{2}', line, re.I) and len(line) < 250:
                    copyrights.add(line.strip(' /*\t'))
        # Original version-specific source declarations stay above; append the full
        # terms they incorporate by reference, not a replacement copyright template.
        if texts and any('apache' in name.lower() for name, _ in meta['licenses']):
            terms = ROOT / 'build/credit-sources/terms/Apache-2.0.txt'
            if terms.is_file():
                texts.append(('https://www.apache.org/licenses/LICENSE-2.0.txt (terms referenced by original declarations)',
                              terms.read_text()))
        if coordinate == 'io.documentnode:epub4j-core:4.2.3':
            terms = ROOT / 'build/credit-sources/terms/GPL-2.0.txt'
            if terms.is_file():
                texts.append(('https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt', terms.read_text()))
            meta['licenses'].append(('GPL-2.0-or-later WITH Classpath-exception-2.0 (bundled JazzLib)',
                                     'https://www.gnu.org/software/classpath/license.html'))
            meta['licenses'].append(('BSD-3-Clause (StreamWriterDelegate, John Kristian)',
                                     'https://repo.maven.apache.org/maven2/io/documentnode/epub4j-core/4.2.3/epub4j-core-4.2.3-sources.jar'))
            meta['authors'] += ['Free Software Foundation (JazzLib/GNU Classpath)',
                                'The Apache Software Foundation (Commons IO)', 'John Kristian (StreamWriterDelegate)']
        slug = re.sub(r'[^a-zA-Z0-9._-]', '_', coordinate)
        paths = save_texts(slug, texts)
        unresolved = []
        if not paths:
            unresolved.append('version-specific LICENSE/NOTICE missing from cached artifact and source')
        if not meta['authors']:
            unresolved.append('POM author/organization missing')
        empty_module = not has_classes(path)
        if not copyrights and not empty_module and not supplemental_override.get('copyrightStatement'):
            unresolved.append('copyright holder statement not located')
        if not meta['licenses']:
            unresolved.append('POM license metadata missing')
        entry = dict(id=coordinate, name=supplemental_override.get('displayName') or f'{group}:{artifact_id}', version=version,
                     authors='; '.join(dict.fromkeys(meta['authors'])) or '待核对：POM 未提供作者',
                     copyright=('\n'.join(sorted(copyrights)) or supplemental_override.get('copyrightStatement') or
                                ('兼容 / 转发模块，不含独立 class 代码；上游未附独立版权行。' if empty_module
                                 else '待核对：未找到版权声明')),
                     license='; '.join(dict.fromkeys(n for n, _ in meta['licenses'])) or '待核对：见离线原文',
                     projectUrl=meta['url'],
                     licenseUrl=supplemental_override.get('url') or next((u for _, u in meta['licenses'] if u), ''), texts=paths,
                     sourceUrl=(meta['url'] if group == 'io.github.zerofancy' else
                                ('https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.')
                                 else 'https://repo1.maven.org/maven2/')
                                + f'{group.replace(".", "/")}/{artifact_id}/{version}/'))
        destination = ASSETS / f'credits/libraries/{slug}.json'
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps([entry], ensure_ascii=False, indent=2) + '\n')
        if coordinate == 'io.documentnode:epub4j-core:4.2.3':
            source_url = 'https://repo.maven.apache.org/maven2/io/documentnode/epub4j-core/4.2.3/epub4j-core-4.2.3-sources.jar'
            embedded = [
                ('jazzlib', 'JazzLib / GNU Classpath (embedded in epub4j)',
                 'epub4j 4.2.3 bundled source snapshot', 'Free Software Foundation; John Leuner; Tom Tromey',
                 'GPL-2.0-or-later WITH Classpath-exception-2.0',
                 'https://www.gnu.org/software/classpath/license.html',
                 [(o, t) for o, t in texts if '/net/sf/jazzlib/' in o or 'gpl-2.0.txt' in o]),
                ('commons-io', 'Apache Commons IO (relocated source subset in epub4j)',
                 'SVN r1005099 / r1052095 / r1052161 / r934041 / r1004112; epub4j 4.2.3 adaptation',
                 'The Apache Software Foundation', 'Apache-2.0',
                 'https://raw.githubusercontent.com/apache/commons-io/commons-io-2.0.1/NOTICE.txt',
                 [(o, t) for o, t in texts if '/commons/io/' in o or 'apache.org/licenses/' in o
                  or 'apache/commons-io/' in o]),
                ('stream-writer-delegate', 'StreamWriterDelegate (embedded in epub4j)',
                 'epub4j 4.2.3 bundled source snapshot', 'John Kristian', 'BSD-3-Clause', source_url,
                 [(o, t) for o, t in texts if 'StreamWriterDelegate.java' in o]),
            ]
            for identifier, name, bundled_version, authors, license_id, license_url, notices in embedded:
                holders = [line.strip(' /*\t') for _, text in notices for line in text.splitlines()
                           if re.search(r'copyright\s+(?:\(c\)\s*)?(?:19|20)\d{2}', line, re.I)]
                component = dict(id=f'embedded:epub4j:{identifier}', name=name, version=bundled_version,
                                 authors=authors, copyright='\n'.join(dict.fromkeys(holders)), license=license_id,
                                 projectUrl=source_url, licenseUrl=license_url, sourceUrl=source_url,
                                 texts=save_texts('embedded_' + identifier, notices))
                destination = ASSETS / f'credits/local/embedded-{identifier}.json'
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(json.dumps([component], ensure_ascii=False, indent=2) + '\n')
        report.append(dict(coordinate=coordinate, artifactSha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                           unresolved=unresolved, textFiles=len(paths)))
    destination = ROOT / 'docs/credits/review.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    # One component per line keeps the inventory readable and below the line limit.
    destination.write_text('[\n' + ',\n'.join(json.dumps(r, ensure_ascii=False) for r in report) + '\n]\n')
    graph = ROOT / 'docs/credits/release-runtime-graph.json'
    graph.write_text('[\n' + ',\n'.join(json.dumps(component, ensure_ascii=False)
                                      for component in data.get('components', [])) + '\n]\n')
    print(f'{len(report)} runtime components; {sum(bool(r["unresolved"]) for r in report)} need metadata review')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    generate(parser.parse_args().inventory)
