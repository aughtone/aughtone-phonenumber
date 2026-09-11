# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The `0.0.x` line is the pre-1.0 (alpha) series; the public API may change before `1.0`.

## [0.0.2] - 2026-09-11

Aligns three `parse()` behaviours with upstream libphonenumber and refreshes the embedded metadata to 9.0.39. A few edge inputs now normalize differently — consumers that derive stable tokens from the E.164 output should review **Changed** before adopting.

### Added
- Public digit-normalization surface a byte-stability-sensitive consumer can pin and reuse: `normalizeDigitsOnly(input, libphonenumberCompat = false)`, `decimalDigitValue(codePoint)`, and `DIGIT_UNICODE_VERSION` (frozen at Unicode **17.0.0**), plus `PhoneNumberUtil.digitUnicodeVersion` ([#4](https://github.com/aughtone/aughtone-phonenumber/issues/2)).
- `libphonenumberCompat` opt-in parameter on `parse()` and `isValid()` — off by default (the more-correct behaviour), on for digit handling identical to upstream ([#4](https://github.com/aughtone/aughtone-phonenumber/issues/2)).
- `NumberParseException.errorType` (`ErrorType.NOT_A_NUMBER` / `INVALID_COUNTRY_CODE`) for machine-readable parse failures ([#1](https://github.com/aughtone/aughtone-phonenumber/issues/2)).

### Changed
- Embedded metadata refreshed **9.0.38 → 9.0.39** (regions BD, HK, IN, PA, PT, TR); verified that no E.164 already produced changes — the update only adds ranges ([#3](https://github.com/aughtone/aughtone-phonenumber/issues/3)).
- Digit normalization now recognises **all** Unicode 17.0.0 decimal digits (77 blocks, including supplementary code points) by default — a strict superset of before, so previously-accepted input is unchanged. Pass `libphonenumberCompat = true` for the earlier BMP-only, upstream-identical behaviour ([#4](https://github.com/aughtone/aughtone-phonenumber/issues/2)).
- `parse()` detects the leading international `+` by a deterministic code-point scan rather than `trim()` / `Char.isWhitespace()`, so classification is identical on every target. Input with leading non-digit characters before a `+` (e.g. `tel:+1…`) is now treated as international ([#2](https://github.com/aughtone/aughtone-phonenumber/issues/2)).
- `NumberParseException` messages are fixed strings and no longer contain the input value, so logging a failed parse cannot leak a phone number; the exception constructor is now internal ([#1](https://github.com/aughtone/aughtone-phonenumber/issues/2)).

### Verified
- Ground-truth against libphonenumber Java **9.0.39**: identical E.164 and `isValid` agreement.
- Zero output drift versus 9.0.38 across the conformance corpus (same inputs → same E.164 and validity).
- Cross-target conformance passes byte-identically on JVM, JS, wasmJs, macOS (native), and iOS simulator.

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
