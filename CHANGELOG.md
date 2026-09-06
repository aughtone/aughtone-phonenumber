# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The `0.0.x` line is the pre-1.0 (alpha) series; the public API may change before `1.0`.

## [0.0.1] - 2026-09-06

First release. A pure Kotlin Multiplatform port of Google's [libphonenumber](https://github.com/google/libphonenumber) focused on byte-stable phone→E.164 normalization.

### Added
- `PhoneNumberUtil.parse(number, defaultRegion)` handling international (`+`), IDD-dialed, and national formats; `PhoneNumber.formatToE164()`; and `isValid(number, defaultRegion)`.
- `METADATA_VERSION` / `PhoneNumberUtil.metadataVersion` exposing the embedded metadata epoch as a stable, readable id.
- Embedded metadata epoch **9.0.38** generated to Kotlin from the pinned libphonenumber XML (254 regions), with no runtime resource loading — so it works on wasmJs and native.
- Own deterministic regex matcher (`PhonePattern`) used on every target, so output is byte-identical across JVM, Android, JS, wasmJs, and native (works around Kotlin/Native + Kotlin/Wasm regex bug [KT-89187](https://youtrack.jetbrains.com/issue/KT-89187); related [KT-57906](https://youtrack.jetbrains.com/issue/KT-57906)).
- Faithful national-prefix and carrier-code stripping with transform rules; deterministic Unicode digit normalization (fullwidth, Arabic-Indic, Eastern Arabic-Indic).
- Region resolution across shared calling codes for `isValid` (per-type patterns), matching libphonenumber's number-type logic.

### Verified
- Ground-truth against libphonenumber Java 9.0.38: 735 dialings across 245 regions produce identical E.164, with `isValid` agreement.
- Cross-target conformance corpus passes byte-identically on JVM, JS, wasmJs, macOS (native), and iOS simulator.

### Packaging
- Targets: JVM, Android, iOS, macOS, tvOS, watchOS, Linux, MingW, JS, and wasmJs.
- Apache-2.0 `LICENSE` and a `NOTICE` attributing the derived work to libphonenumber.
- Maven Central publication and Swift Package Manager (XCFramework) integration via the `vanniktech-mavenPublish` plugin.
