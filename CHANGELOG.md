# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The `0.0.x` line is the pre-1.0 (alpha) series; the public API may change before `1.0`.

## [Unreleased]

### Added
- **`isSupportedRegion(regionCode)`** ([#21](https://github.com/aughtone/aughtone-phonenumber/issues/21)): an O(1) convenience that returns true only for a supported geographic region — the same set as `getSupportedRegions()`, excluding the non-geographical entity (`"001"`) and unknown codes. Prefer it over probing region validity with `parse("+…", region)`, which is not a region-validity test ([#13](https://github.com/aughtone/aughtone-phonenumber/issues/13)).

### Fixed
- **Durchwahl guard no longer refuses ordinary space-grouped valid numbers** ([#22](https://github.com/aughtone/aughtone-phonenumber/issues/22)). 0.0.3's #6 ambiguity guard fired whenever a number was valid both with and without its last formatting-separated group; in a variable-length dialling plan the leading part of a normally-formatted number is frequently valid on its own, so ordinary numbers such as `+49 89 636 48018` (DE) were wrongly refused with `AMBIGUOUS_TRAILING_GROUP`. The guard is now narrowed to the actual Durchwahl signal — a **hyphen**-separated trailing group, or a whole number that is otherwise invalid; a space alone is not evidence. Hyphen cases are unchanged (`+49 30 12345678-12`, `+43 1 58058-0` still refuse; `+41 44 123 45 67-8` still resolves to extension `8`), and `formatToE164()` is unchanged for numbers that already parsed. Pass `libphonenumberCompat = true` to fold like upstream, as before.

## [0.0.3] - 2026-09-19

Completes the parse / validate / format port ([#7](https://github.com/aughtone/aughtone-phonenumber/issues/7)). With `libphonenumberCompat = true` the covered surface is byte-identical to libphonenumber 9.0.39 and passes upstream's own tests for it; the default mode does the more-correct thing. `formatToE164()` is unchanged for numbers that already parsed, so existing E.164 tokens are stable. The one behavioural change to review is the Durchwahl refusal under **Changed**.

### Added
- **Formatting API** ([#14](https://github.com/aughtone/aughtone-phonenumber/issues/14)): `format(number, PhoneNumberFormat)` for `NATIONAL` / `INTERNATIONAL` / `RFC3966` / `E164`, plus `formatOutOfCountryCallingNumber`, `formatNationalNumberWithCarrierCode` / `…WithPreferredCarrierCode`, `formatByPattern`, `formatNumberForMobileDialing`, `formatInOriginalFormat`, and `formatOutOfCountryKeepingAlphaChars`. New `PhoneNumberFormat` enum and public `NumberFormat`. Display-format templates embedded in the metadata.
- **Number type & validity** ([#15](https://github.com/aughtone/aughtone-phonenumber/issues/15)): `getNumberType`, `isValidNumber`, `isValidNumberForRegion`, `getSupportedTypesForRegion`, `getSupportedTypesForNonGeoEntity`, and the `PhoneNumberType` enum.
- **Possible-length checks** ([#16](https://github.com/aughtone/aughtone-phonenumber/issues/16)): `isPossibleNumber` / `isPossibleNumberForType` (+`WithReason`), `truncateTooLongNumber`, and the `ValidationResult` enum.
- **`isNumberMatch`** ([#17](https://github.com/aughtone/aughtone-phonenumber/issues/17)) in three overloads (number/number, number/string, string/string) with the `MatchType` enum.
- **Raw-input parsing** ([#18](https://github.com/aughtone/aughtone-phonenumber/issues/18)): `parseAndKeepRawInput`. `PhoneNumber` gains `extension`, `italianLeadingZero`, `numberOfLeadingZeros`, `rawInput`, `countryCodeSource` and `preferredDomesticCarrierCode`; new `CountryCodeSource` enum. None of these affect `formatToE164()`.
- **Accessors & helpers** ([#19](https://github.com/aughtone/aughtone-phonenumber/issues/19)): `getSupportedRegions` / `…CallingCodes` / `…GlobalNetworkCallingCodes`, `getRegionCodeForNumber` / `…ForCountryCode`, `getRegionCodesForCountryCode`, `getCountryCodeForRegion`, `getNddPrefixForRegion`, `getNationalSignificantNumber`, `getCountryMobileToken`, `isNANPACountry`, `isNumberGeographical`, `isAlphaNumber`, `canBeInternationallyDialled`, `isMobileNumberPortableRegion`, `getLengthOfGeographicalAreaCode` / `…NationalDestinationCode`, `getExampleNumber` / `…ForType` / `…ForNonGeoEntity`, `getInvalidExampleNumber`, `convertAlphaCharactersInNumber`, `normalizeDiallableCharsOnly`.
- **`parse` input handling** now matches upstream: alphabetic vanity numbers are keypad-converted ([#9](https://github.com/aughtone/aughtone-phonenumber/issues/9)), extensions are split off and returned on `PhoneNumber.extension` ([#5](https://github.com/aughtone/aughtone-phonenumber/issues/5)), and RFC 3966 `tel:` URIs with `;phone-context=` are resolved ([#8](https://github.com/aughtone/aughtone-phonenumber/issues/8)). `ErrorType` gains `TOO_SHORT_AFTER_IDD`, `TOO_SHORT_NSN` and `TOO_LONG` ([#12](https://github.com/aughtone/aughtone-phonenumber/issues/12)).
- `PhoneNumber.extension` is normalized to ASCII by the same rule as the national number: every Unicode `Nd` decimal digit (all 77 blocks in the default mode, BMP-only under `libphonenumberCompat`) is folded to ASCII, so the extension is byte-stable across scripts — a deliberate divergence from upstream, which keeps the raw typed characters. (Previously non-ASCII extension digits were not recognised and folded into the national number.) An absent or empty extension is `null`, never `""` — a captured extension always has at least one digit.
- `ErrorType.AMBIGUOUS_TRAILING_GROUP` ([#6](https://github.com/aughtone/aughtone-phonenumber/issues/6)) so the default-mode Durchwahl refusal is machine-distinguishable from an ordinary `NOT_A_NUMBER`.

### Changed
- **Ambiguous direct-dial (Durchwahl) numbers are refused in the default mode** rather than silently folded ([#6](https://github.com/aughtone/aughtone-phonenumber/issues/6)). When an input carries a hyphen/space-separated trailing group and the number is valid both with and without it (as in `+49 30 12345678-12`, `+43 1 58058-0`), `parse` throws `ErrorType.AMBIGUOUS_TRAILING_GROUP`; when only the base is valid the group becomes the extension. Fixed-length plans (US, GB, …) are unaffected. Pass `libphonenumberCompat = true` to fold like upstream.
- **Alphabetic vanity conversion is new** ([#9](https://github.com/aughtone/aughtone-phonenumber/issues/9)): 0.0.2 dropped letters, 0.0.3 keypad-converts them (matching upstream — `1-800-FLOWERS` → `1-800-3569377`). A side effect worth knowing when calling `parse` directly: any run of three or more letters is folded into digits, so incidental words in the input become part of the number. Refuse or strip letters upstream of `parse` if that is a risk for your inputs.
- Country-code extraction rewritten to upstream's algorithm, adding `TOO_SHORT_AFTER_IDD` and the leading-`+`-then-IDD handling ([#10](https://github.com/aughtone/aughtone-phonenumber/issues/10)); national-prefix stripping keeps the stripped form only when it stays a possible length ([#11](https://github.com/aughtone/aughtone-phonenumber/issues/11)).
- `PhoneNumber` is read-only from the public API: its constructor and generated `copy()` are internal (`@ConsistentCopyVisibility`); instances come only from parsing.

### Verified
- Upstream parity in compat mode against libphonenumber Java **9.0.39** across dedicated harnesses for parse→E.164, formatting (all forms, out-of-country, carrier, by-pattern, mobile-dialing, original-format, keeping-alpha), number type & validity, possible-length, `isNumberMatch`, and the accessors.
- `formatToE164()` output unchanged versus 0.0.2 for every conformance-corpus example.
- Cross-target byte-stability (parse and format) on JVM, JS, wasmJs, macOS (native), and iOS simulator.

### Upgrading from 0.0.2
- `ErrorType` gained four members (`TOO_SHORT_AFTER_IDD`, `TOO_SHORT_NSN`, `TOO_LONG`, `AMBIGUOUS_TRAILING_GROUP`). A Kotlin `when` over `ErrorType` that was exhaustive will stop compiling until the new branches (or an `else`) are added — intentional, so each new failure mode is handled deliberately rather than swallowed.
- To test whether a region code is supported, use `getSupportedRegions()` (or `getCountryCodeForRegion(region)`, which returns 0 for an unknown region). Do **not** probe with `parse("+…", region)`: a `+`-prefixed number carries its own calling code and parses regardless of the region argument ([#13](https://github.com/aughtone/aughtone-phonenumber/issues/13)), so it is not a region-validity test — in 0.0.2 it happened to throw for an unusable region, and no longer does.

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
