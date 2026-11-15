#!/usr/bin/env python3
"""Offline verification of fixed official font assets; never downloads at build/runtime."""
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"


def verify():
    manifest = json.loads((ASSETS / "fonts/manifest.json").read_text(encoding="utf-8"))
    total = 0
    for entry in manifest["fonts"]:
        path = ASSETS / entry["assetPath"]
        data = path.read_bytes()
        assert len(data) == entry["bytes"], f"Size mismatch: {path.name}"
        assert hashlib.sha256(data).hexdigest() == entry["sha256"], f"Hash mismatch: {path.name}"
        assert data[:4] in (b"\x00\x01\x00\x00", b"OTTO"), f"Not static SFNT: {path.name}"
        count = struct.unpack_from(">H", data, 4)[0]
        tags = set()
        ranges = []
        for i in range(count):
            tag, _, offset, size = struct.unpack_from(">4sIII", data, 12 + 16 * i)
            assert 12 + count * 16 <= offset <= offset + size <= len(data), path.name
            assert tag not in tags, f"Duplicate table: {path.name}"
            tags.add(tag)
            if size:
                ranges.append((offset, offset + size))
        ranges.sort()
        assert all(a[1] <= b[0] for a, b in zip(ranges, ranges[1:])), path.name
        assert not tags.intersection((b"fvar", b"gvar", b"avar", b"CFF2")), path.name
        license_data = (ASSETS / entry["licenseAssetPath"]).read_bytes()
        assert b"SIL OPEN FONT LICENSE Version 1.1" in license_data
        assert len(license_data) == entry["licenseBytes"]
        assert hashlib.sha256(license_data).hexdigest() == entry["licenseSha256"]
        assert entry["version"] in entry["sourceUrl"], "Download must pin a release"
        total += len(data)
        print(f"OK {path.name}: {len(data):,} bytes; SHA-256 {entry['sha256']}")
    assert total == manifest["totalFontBytes"]
    assert len(manifest["fonts"]) == 3
    print(f"Verified three unchanged static Regular fonts: {total:,} bytes total")


if __name__ == "__main__":
    verify()
