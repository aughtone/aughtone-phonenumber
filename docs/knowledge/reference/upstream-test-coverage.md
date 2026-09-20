# Upstream test coverage

Every test method in libphonenumber's own suite, its status in this port, and the ticket that owns it. This is the artifact [#7](https://github.com/aughtone/aughtone-phonenumber/issues/7) is built to keep honest: nothing upstream tests is unaccounted for. The parity contract is [ADR-0001](../decisions/ADR-0001-scope-of-the-libphonenumber-port.md) — with `libphonenumberCompat = true`, behaviour must be byte-identical to upstream, and every in-scope test must pass with no skips before a release claims parity.

Measured against `libphonenumber-java` 9.0.39 by `UpstreamParityTest` (jvmTest), which feeds upstream's own inputs through this port in compat mode and compares to the reference. Live tally at last run: **127 input cases, 127 matching, 0 gaps** — the parse/validate scope (ADR-0001) is fully green; the harness now stands as a regression guard.

Legend: **green** = compat parity holds now · **needs-API** = in scope, not yet expressible because the API is unbuilt · **exempt** = tests an architecture we deliberately replaced (embedded metadata), documented not skipped.

## PhoneNumberUtilTest (121 methods)

### Green — parity holds in compat mode
The whole parse/validate core: `testParseNationalNumber` (all rows — national, `+`, IDD, `tel:`/RFC3966, the wild `64(0)…` form), `testParseWithInternationalPrefixes`, `testParseNonAscii`, `testParseNationalNumberArgentina`, `testParseWithXInNumber`, `testParseNumbersMexico`, `testParseWithLeadingZero`, `testNormaliseOtherDigits`, `testFormatE164Number`, and every method that was previously a per-ticket gap:

| ticket | upstream methods now green |
|---|---|
| [#5](https://github.com/aughtone/aughtone-phonenumber/issues/5) extensions | `testParseExtensions`, `testParseHandlesLongExtensionsWithExplicitLabels`, `testParseHandlesLongExtensionsWithAutoDiallingLabels`, `testParseHandlesShortExtensionsWithAmbiguousChar`, `testParseHandlesShortExtensionsWhenNotSureOfLabel` |
| [#8](https://github.com/aughtone/aughtone-phonenumber/issues/8) RFC3966 | `testParseWithPhoneContext`; the `tel:` rows of `testParseNationalNumber`; internal `testExtractPossibleNumber` |
| [#9](https://github.com/aughtone/aughtone-phonenumber/issues/9) alpha/vanity | `testParseNumberWithAlphaCharacters` |
| [#10](https://github.com/aughtone/aughtone-phonenumber/issues/10) leading-`+`-then-IDD | the `+IDD` rows of `testParseNationalNumber`; internal `testMaybeStripInternationalPrefix`, `testMaybeExtractCountryCode` (`TOO_SHORT_AFTER_IDD`, keep-better-result) |
| [#11](https://github.com/aughtone/aughtone-phonenumber/issues/11) strip guard | `testParseNumberTooShortIfNationalPrefixStripped`, `testParseItalianLeadingZeros`; internal `testMaybeStripNationalPrefix` |
| [#12](https://github.com/aughtone/aughtone-phonenumber/issues/12) viability/length/errors | `testParseMaliciousInput`, `testFailedParseOnInvalidNumbers`, `testIsViablePhoneNumber`, `testIsViablePhoneNumberNonAscii`, `testUnknownCountryCallingCode` |
| [#13](https://github.com/aughtone/aughtone-phonenumber/issues/13) `+` with unknown region | `testParseNumbersWithPlusWithNoRegion` |
| [#14](https://github.com/aughtone/aughtone-phonenumber/issues/14) formatting API | `testFormat{US,GB,DE,IT,AU,AR,MX,BS}Number`, `testFormatOutOfCountryCallingNumber`, `testFormatOutOfCountryWithInvalidRegion`, `testFormatOutOfCountryWithPreferredIntlPrefix`, `testFormatWithCarrierCode`, `testFormatWithPreferredCarrierCode`, `testFormatByPattern`, `testFormatNumberWithExtension`, `testFormatNumberForMobileDialing` — verified by `FormatParityTest`; plus `testFormatInOriginalFormat` and `testFormatOutOfCountryKeepingAlphaChars` — verified by `RawInputParityTest` (the only remaining `testFormat*` is `testFormatAUShortCodeNumber`, ShortNumberInfo, RAD-0003) |
| [#15](https://github.com/aughtone/aughtone-phonenumber/issues/15) number-type & validity | `testGetSupportedTypesForRegion`, `testGetSupportedTypesForNonGeoEntity`, `testIsPremiumRate`, `testIsTollFree`, `testIsMobile`, `testIsFixedLine`, `testIsFixedLineAndMobile`, `testIsSharedCost`, `testIsVoip`, `testIsPersonalNumber`, `testIsUnknown`, `testIsValidNumber`, `testIsValidForRegion`, `testIsNotValidNumber`, `testCountryWithNoNumberDesc` — verified by `NumberTypeParityTest` (per-type example numbers for every region + non-geo entity: `getNumberType`/`isValidNumber`/`isValidNumberForRegion`/`getSupportedTypes*`, plus invalid-number cases) |
| [#17](https://github.com/aughtone/aughtone-phonenumber/issues/17) isNumberMatch | `testIsNumberMatchMatches`, `testIsNumberMatchShortMatchIfDiffNumLeadingZeros`, `testIsNumberMatchAcceptsProtoDefaultsAsMatch`, `testIsNumberMatchMatchesDiffLeadingZerosIfItalianLeadingZeroFalse`, `testIsNumberMatchIgnoresSomeFields`, `testIsNumberMatchNonMatches`, `testIsNumberMatchNsnMatches`, `testIsNumberMatchShortNsnMatches` — verified by `NumberMatchParityTest` (string, PhoneNumber/string and direct-construction pairs) + `NumberMatchTest` (cross-target) |
| [#19](https://github.com/aughtone/aughtone-phonenumber/issues/19) accessors & helpers | `testGetSupportedRegions`, `testGetSupportedCallingCodes`, `testGetSupportedGlobalNetworkCallingCodes`, `testIsNumberGeographical`, `testGetLengthOfGeographicalAreaCode`, `testGetLengthOfNationalDestinationCode`, `testGetCountryMobileToken`, `testGetNationalSignificantNumber`×2, `testGetExampleNumber`, `testGetInvalidExampleNumber`, `testGetExampleNumberForNonGeoEntity`, `testGetExampleNumberWithoutRegion`, `testGetRegionCodeForCountryCode`, `testGetRegionCodeForNumber`, `testGetRegionCodesForCountryCode`, `testGetCountryCodeForRegion`, `testGetNationalDiallingPrefixForRegion`, `testIsNANPACountry`, `testCanBeInternationallyDialled`, `testIsAlphaNumber`, `testIsMobileNumberPortableRegion`, `testConvertAlphaCharactersInNumber`, `testNormaliseRemovePunctuation`, `testNormaliseReplaceAlphaCharacters`, `testNormaliseStripAlphaCharacters`, `testNormaliseStripNonDiallableCharacters` — verified by `AccessorsParityTest` (all supported regions/calling codes/example numbers) + `NormalizeHelpersTest` (cross-target) |
| [#16](https://github.com/aughtone/aughtone-phonenumber/issues/16) isPossible & possible-length | `testIsPossibleNumber`, `testIsPossibleNumberForType`×4, `testIsPossibleNumberWithReason`, `testIsPossibleNumberForTypeWithReason`×5, `testIsNotPossibleNumber`, `testTruncateTooLongNumber` — verified by `PossibleNumberParityTest` |
| [#18](https://github.com/aughtone/aughtone-phonenumber/issues/18) value object & raw input | `PhonenumberTest` intent (`PhoneNumberValueTest`), `testParseAndKeepRaw` — verified by `RawInputParityTest` + `RawInputTest` (cross-target) |

### Needs-API — in scope, not yet expressible
_None — the whole parse/validate/format scope (ADR-0001) is now covered. The only in-suite methods not ported are the exemptions below._

### Exempt — tests an architecture we replaced with embedded metadata
`testGetInstanceLoadBadMetadata`, `testGetInstanceLoadUSMetadata`, `testGetInstanceLoadDEMetadata`, `testGetInstanceLoadARMetadata`, `testGetInstanceLoadInternationalTollFreeMetadata`, `testGetMetadataForRegionForNonGeoEntity`, `testGetMetadataForRegionForUnknownRegion`, `testGetMetadataForNonGeographicalRegionForGeoRegion`, `testGetMetadataForRegionForMissingMetadata`, `testGetMetadataForNonGeographicalRegionForMissingMetadata`.

These exercise the runtime metadata **loader/reader** (loading a region's metadata from a resource, handling a missing or bad metadata file, reading `PhoneMetadata` back out). This port embeds metadata as Kotlin and exposes no loader or `PhoneMetadata` accessor, so the tests cannot run as written — the very design ([ADR-0001](../decisions/ADR-0001-scope-of-the-libphonenumber-port.md)) that makes wasmJs/native work. Honest caveat: their *data-level* assertions (that US/DE/AR metadata has particular values) could be re-expressed against `GENERATED_METADATA` if we ever want the extra check; they are exempt as *loader* tests, not as data checks.

## Other upstream test files

| file | methods | disposition |
|---|---|---|
| `PhonenumberTest` | 7 | [#18](https://github.com/aughtone/aughtone-phonenumber/issues/18) — the `PhoneNumber` value object |
| `ExampleNumbersTest` | 18 | partly satisfied by this port's `ConformanceTest` + `GroundTruthTest`; remainder under [#15](https://github.com/aughtone/aughtone-phonenumber/issues/15)/[#19](https://github.com/aughtone/aughtone-phonenumber/issues/19) |
| `AsYouTypeFormatterTest` | 33 | **out of scope** — [RAD-0001](../research/RAD-0001-porting-asyoutypeformatter.md) |
| `PhoneNumberMatcherTest` + `PhoneNumberMatchTest` | 48 | **out of scope** — [RAD-0002](../research/RAD-0002-porting-phonenumbermatcher.md) |
| `ShortNumberInfoTest` | 28 | **out of scope** — [RAD-0003](../research/RAD-0003-porting-shortnumberinfo.md) |
| `metadata/source/*`, `metadata/init/*` | ~28 | **exempt** — test the resource-loader machinery embedded metadata replaces |
| `internal/{Matcher,RegexCache,GeoEntityUtility}Test` | ~11 | exempt — we ship our own `PhonePattern`; GeoEntity is geocoding, out of scope |

## Summary

Of `PhoneNumberUtilTest`'s 121 methods: 8 green now, ~13 ported and red (spec for #5/#8–#13), ~90 in-scope needing an unbuilt API (#14–#19), and 10 exempt as loader tests. Every one is on this page with an owner. The three big subsystems (AsYouTypeFormatter, PhoneNumberMatcher, ShortNumberInfo — 109 methods) are out of scope with a RAD each. "No skipped tests in a release" is measured against this matrix.
