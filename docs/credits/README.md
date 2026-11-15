# Offline third-party credits

## Release preparation

1. Apply `scripts/credits.gradle` from the application Gradle script.
2. Run `./gradlew :app:exportReleaseCreditsInventory` (no APK compilation).
3. Fetch exact-version evidence (preparation only; never at app runtime):
   - `python3 scripts/fetch_credit_sources.py app/build/reports/credits/release-runtime.json`
   - `python3 scripts/fetch_credit_licenses.py app/build/reports/credits/release-runtime.json`
   - `python3 scripts/fetch_credit_parents.py app/build/reports/credits/release-runtime.json`
   - `python3 scripts/generate_library_credits.py app/build/reports/credits/release-runtime.json`
4. Review `docs/credits/review.json`; supplement actual version-specific missing
   LICENSE/NOTICE/copyright evidence rather than using generic license templates.
5. Run `python3 scripts/verify_credits.py app/build/reports/credits/release-runtime.json`.
   A nonzero result is an explicit release blocker, not a successful audit.

Release `preReleaseBuild` automatically depends on `verifyReleaseCredits` and
`verifyBundledFonts`: the actual dependency graph, reviewed artifact SHA-256
(including mutable snapshots), offline declaration assets, pinned font binaries
and license content are checked before packaging. License text may use normal
line-ending conversion; upstream license hashes are provenance metadata, not a
byte-identity requirement. Metadata fetching/regeneration remains a maintainer
preparation step, not an app runtime network operation.

The Gradle inventory records the resolved release graph, module versions, runtime
artifact paths and POM paths. Platforms/BOMs without runtime artifacts are not
shown as shipped libraries. Local project code is represented separately:
PTQFlipper 1.1.0, adapted locally, with its original MIT license asset.
Build/test/debug-only dependencies are excluded by releaseRuntimeClasspath.

The Python generator extracts LICENSE/NOTICE files from the actual AAR/JAR and
matching cached source JARs, and authors/license URLs from the matching POM.
It retains distinct texts and source-file copyright statements. Large license
texts are split at 450 lines and concatenated without omitting content by the
About screen. Each artifact has its own entry and SHA-256 in the review report.
`credits/libraries` and `credits/texts` are generator-owned directories and are
replaced on regeneration. Font and local module entries are kept separately.

## Current audit limitations

The committed inventory is generated from the successful release runtime task:
131 unique external runtime coordinates, plus local PTQFlipper, embedded code
entries and three bundled fonts. `release-runtime-graph.json` preserves resolved
edges (including non-shipped platforms). No stale lint-model entries remain.
The review report exposes missing evidence rather than claiming a legal audit.
A resolved graph is necessary but not sufficient: new shaded code/resources
still require manual review after dependency changes.

The mutable local Maven `kmp-updater:0.1.0-SNAPSHOT` archive was matched byte for
byte for all six Kotlin sources to commit
`e70e60affae97158115cc822bbacd07f95eb5420` in the local updater repository.
That revision's LICENSE equals the inspected MIT / Copyright 2026 zerofancy
file (SHA-256 `0ebd0bd44b82f87818b0814f8fcbb01d42772dfa4b7e9c22acb0e9619bac6f06`).
The inventory stores the actual runtime artifact hash; rebuilding a changed
snapshot must regenerate/review credits, not reuse its version label blindly.

Embedded source review found obligations not represented by POM license names:

- DataStore's repackaged Protobuf has runtime constants `4.36.1`; its original
  `v36.1` BSD-3-Clause license includes Copyright 2008 Google Inc.
- epub4j 4.2.3 includes JazzLib/GNU Classpath GPL-2.0-or-later with the Classpath
  linking exception. Original exceptions/copyright blocks and full GPL-2.0 are
  included, with the exact published corresponding-source JAR URL. Distribution
  still requires review of GPL source-availability obligations; this is not a
  blanket assertion that every release process satisfies them.
- epub4j also includes relocated Commons IO source with SVN revisions
  r1005099/r1052095/r1052161/r934041/r1004112. These match the revision labels in
  Commons IO 2.0.1 sources (code is adapted, not claimed byte-identical). Its
  original source declarations and Commons IO 2.0.1 NOTICE are preserved.
- StreamWriterDelegate retains John Kristian's 2006 BSD-3-Clause declaration.
- minilog 1.0's actual LICENSE and source files contain no independent copyright
  line. The UI states this explicitly and names POM author Jake Wang; it does not
  invent a year or claim the generic Apache appendix identifies a holder.
- Empty compatibility/forwarding artifacts are identified as containing no class
  code; their POM organization and packaged license are preserved without
  inventing separate copyright statements.

## UI integration

`AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier)` loads everything
from assets on an IO dispatcher. It has no dependency on reading font state.
It consumes `credits/fonts.json` (array), per-component arrays under
`credits/libraries`, and arrays under `credits/local`. Each entry has string
fields `id`, `name`, `version`, `authors`, `copyright`, `license`, `projectUrl`,
`licenseUrl`, optional `sourceUrl`, and a `texts` array of asset-relative paths.
The font implementation owns its three entries and actual license resources.
Missing assets show a retryable error, never an empty-success catalog.

External links open only upon explicit taps and only for HTTP(S) URLs; absent
handlers and security errors show a snackbar without leaving the About route.
All URLs can be selected/copied. Expanded offline license/NOTICE text is
scrollable, selectable and copyable. Device accessibility/browser-offline
behavior still requires instrumentation/manual acceptance testing.
