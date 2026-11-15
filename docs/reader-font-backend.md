# Reader font backend

## Integration contract

`data.fonts.FontRepository(context)` owns only resources and the private catalog.
It does not mutate `ReaderSettings` or apply fonts to UI text.

- `catalog: StateFlow<FontCatalogState>` always includes system and the three
  builtins; `loading` and `error` distinguish unread/failed catalogs from empty
  imported lists. Call `refresh()` at initialization or retry. Builtins and system
  remain resolvable if reading the imported catalog fails.
- `resolve(id): ResolvedReaderFont` suspends for IO and platform loading. Its
  `family` is the exact Compose family to share between measurement and drawing;
  `cacheKey` includes the resource hash. Cache capacity is three resolved fonts.
- `importFont(uri): FontImportResult` copies from a SAF URI without retaining
  permission. A duplicate returns the existing `entry` and `duplicate=true`;
  `builtin` distinguishes a bundled match. Import does not select the font.
- `deleteImported(id): FontDeletionResult` requires an imported internal ID. The
  selection coordinator **must first resolve and persist system** if this is the
  selected font. `cleanupPending=true` means catalog removal succeeded but file
  deletion did not; next successful startup reconciliation retries orphan cleanup.
- `readLicense(assetPath)` reads the three known builtin license assets offline.
  About metadata is in `assets/credits/fonts.json`; resource provenance, source
  URLs, bytes and SHA-256 values are in `assets/fonts/manifest.json`.
- `FontException.code: FontError` supplies a Chinese `userMessage`.
  `isPermanentSelectionFailure` distinguishes missing/malformed/unloadable resources
  from transient storage IO, security faults or catalog failures; only permanent
  failures justify overwriting the selected setting. Private file checks use
  errno-aware `Os.stat`, not `File.isFile` (which conflates IO with absence).
  Cancellation propagates rather than reporting a font error.

The settings coordinator should serialize selection commit and deletion, discard
stale selection results, persist the ID only after successful resolution, and
publish the new resolved snapshot only after persistence succeeds. Unknown IDs,
missing files and permanent corruption should select/persist system with one
notice. A catalog read error is not an empty catalog and should offer retry;
transient IO should not delete user data or be treated as permanent corruption.

## Storage and validation

Imported immutable files and the versioned `AtomicFile` JSON catalog live only in
`noBackupFilesDir/fonts`. Controlled UUID-derived names are validated on every
catalog read. Imports stream at most 50 MiB plus the single byte needed to detect
oversize, hashing actual bytes. Validation checks SFNT signatures/table bounds,
required outlines/tables and static format, rejects TTC/OTC, WOFF/WOFF2 and
variable-font tables, then builds an Android Typeface and measures a sample.
Names are cleaned and limited to 80 Unicode code points; they never become paths.

An import commits temporary -> formal immutable file -> atomic catalog. Failure
rolls back the new file when possible. Delete commits catalog before removing the
file. Only a successful catalog read authorizes cleanup of controlled orphan and
temporary files. A corrupt catalog remains in place; formal files are retained.
A defensive 16 MiB catalog decode limit protects memory; no font-count limit is
imposed by the API. User-selected files are parsed by the platform; this is not a
complete malicious-font detector and does not promise every Chinese glyph exists.

## Resource and test status

Exactly one unchanged official static Regular per family is packaged. Zhuque is
upstream's official v0.212 technical preview; Chill uses the original non-Pro
v1.200 Regular; WenKai is original non-Mono v1.530, not Lite/Screen/GB. Original
version licenses, including LXGW's RFN/additional permission, remain verbatim.
The bundled raw font total is **47,674,716 bytes (45.47 MiB)**. This is not an APK
increment. Paired release APK and installed-device code directory measurements
are recorded in the [device verification report](reader-font-verification.md),
not embedded into the measured APK itself.

Run offline resource verification:

```sh
python3 scripts/verify_font_resources.py
```

JVM `FontValidationTest` covers hashes, real 50 MiB unknown-size boundaries,
zero-read providers, read interruptions, names, SFNT structure, unsupported formats,
variable axes, malformed/overlapping offsets and random short malformed inputs.
`FontFailureTest` checks permanent-vs-retryable classification and cyclic causes.
Android `FontRepositoryTest` covers real platform loading, builtin/import
deduplication, source independence, restart/delete, corrupt catalog preservation,
traversal rejection, AtomicFile recovery/failed commits, provider interruption and
deterministic cancellation cleanup. Connected NX888J/API 36 tests, UI/reader
reflow checks, real SAF import/restart, performance observations and paired package
measurements are recorded in the [device verification report](reader-font-verification.md).
Full disk, exhaustive process-kill timing and other device/API combinations remain
explicitly unverified; see that report for the exact scope.
