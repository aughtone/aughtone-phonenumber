/*
 * Copyright (C) 2009 The Libphonenumber Authors
 * Copyright 2026 The Aught One Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications: ported from the original Java implementation to Kotlin
 * Multiplatform. Behaviour follows the original across the parse / validate /
 * format surface; with `libphonenumberCompat = true` it is byte-identical to
 * upstream, and the default does the more-correct thing (see the README).
 */
package io.github.aughtone.phonenumber

/** How the country calling code was derived when parsing. Mirrors libphonenumber's `CountryCodeSource`. */
public enum class CountryCodeSource {
    FROM_NUMBER_WITH_PLUS_SIGN,
    FROM_NUMBER_WITH_IDD,
    FROM_NUMBER_WITHOUT_PLUS_SIGN,
    FROM_DEFAULT_COUNTRY,
    UNSPECIFIED,
}

/**
 * A parsed phone number: the country calling code and the national significant number, plus the
 * optional fields libphonenumber's `PhoneNumber` carries. Unlike upstream's mutable builder this
 * is an immutable value object — an intentional API divergence (byte-stable normalization wants
 * immutability); the *data* it holds mirrors upstream.
 *
 * [extension], [rawInput], [countryCodeSource] and [preferredDomesticCarrierCode] are populated by
 * the features that own them (extensions #5, raw-input parsing #18) and default to unset otherwise.
 * None of them affects [formatToE164]: E.164 has no slot for an extension or these fields.
 *
 * Instances come only from parsing; the constructor and the generated `copy()` are internal, so the
 * public surface is read-only. ([ConsistentCopyVisibility] makes `copy()` follow the constructor.)
 */
@ConsistentCopyVisibility
public data class PhoneNumber internal constructor(
    val countryCode: Int,
    val nationalNumber: String,
    /** Phone extension (digits only), or null if none. Never part of the E.164 form. */
    val extension: String? = null,
    /** True when the national number has a meaningful leading zero (e.g. Italian fixed lines). */
    val italianLeadingZero: Boolean = false,
    /** Number of leading zeros when [italianLeadingZero] is true; 1 otherwise. */
    val numberOfLeadingZeros: Int = 1,
    /** The raw input string, when parsed via a raw-input-keeping parse; null otherwise. */
    val rawInput: String? = null,
    /** How the country code was determined during parsing. */
    val countryCodeSource: CountryCodeSource = CountryCodeSource.UNSPECIFIED,
    /** Carrier-selection code the caller must dial domestically, when known. */
    val preferredDomesticCarrierCode: String? = null,
) {
    /** Canonical E.164, e.g. "+16502530000". Byte-stable within a released version. */
    public fun formatToE164(): String = "+$countryCode$nationalNumber"
}

/**
 * Byte-stable phone-number parser, validator and formatter driven by the embedded
 * [GENERATED_METADATA]. It ports libphonenumber's parse / validate / format surface: parsing
 * (international "+", IDD and national forms, RFC3966 `tel:` URIs, alpha/vanity numbers, extensions,
 * full-Unicode digits), number typing and validity ([getNumberType], [isValidNumber]), possible-length
 * checks ([isPossibleNumber]), the formatting family ([format], out-of-country, by-pattern, carrier,
 * mobile-dialing, original-format), [isNumberMatch], and the read-only accessors.
 *
 * With `libphonenumberCompat = true` the covered behaviour is byte-identical to upstream; the default
 * does the more-correct thing (see the README's compatibility section). Not covered, by design
 * (see the RADs): AsYouTypeFormatter, PhoneNumberMatcher, and ShortNumberInfo.
 */
public object PhoneNumberUtil {

    /** The embedded libphonenumber metadata version (see [METADATA_VERSION]). */
    public val metadataVersion: String get() = METADATA_VERSION

    /** The Unicode version of the embedded decimal-digit table (see [DIGIT_UNICODE_VERSION]). */
    public val digitUnicodeVersion: String get() = DIGIT_UNICODE_VERSION

    /** Why a parse failed. Mirrors libphonenumber's `NumberParseException.ErrorType`. */
    public enum class ErrorType {
        INVALID_COUNTRY_CODE, NOT_A_NUMBER, TOO_SHORT_AFTER_IDD, TOO_SHORT_NSN, TOO_LONG,
    }

    // Bounds from libphonenumber: an NSN is 2..17 digits, and the raw input is capped to guard
    // against pathological input.
    private const val MIN_LENGTH_FOR_NSN = 2
    private const val MAX_LENGTH_FOR_NSN = 17
    private const val MAX_INPUT_STRING_LENGTH = 250
    private const val MAX_LENGTH_COUNTRY_CODE = 3

    /**
     * Thrown when a string cannot be parsed. The message is a fixed string and
     * never contains the input, so logging the exception cannot leak a phone
     * number; [errorType] carries the machine-readable reason.
     */
    public class NumberParseException internal constructor(
        public val errorType: ErrorType,
        message: String,
    ) : Exception(message)

    /**
     * Parse [number] as dialed from [defaultRegion] (an ISO region code such as
     * "US"). Handles "+" international form, IDD-prefixed international form, and
     * national form. Returns the number reduced for E.164 formatting.
     *
     * @param libphonenumberCompat when true, digit normalization matches upstream
     *   libphonenumber exactly (BMP decimal digits only; see [normalizeDigitsOnly]).
     *   Off by default: the default recognises all Unicode
     *   [DIGIT_UNICODE_VERSION] decimal digits, which is a strict superset of the
     *   inputs upstream accepts. The flag is a per-call option, never global, so
     *   output stays byte-stable for a given (input, flag) pair.
     */
    public fun parse(
        number: String,
        defaultRegion: String,
        libphonenumberCompat: Boolean = false,
    ): PhoneNumber = parseInternal(number, defaultRegion, libphonenumberCompat, keepRawInput = false)

    /**
     * As [parse], but also records where the number came from: [PhoneNumber.rawInput] is set to
     * [number], [PhoneNumber.countryCodeSource] to how the country code was determined, and
     * [PhoneNumber.preferredDomesticCarrierCode] to any carrier-selection code stripped while parsing.
     * Mirrors libphonenumber's `parseAndKeepRawInput`; these fields never enter [PhoneNumber.formatToE164].
     */
    public fun parseAndKeepRawInput(
        number: String,
        defaultRegion: String,
        libphonenumberCompat: Boolean = false,
    ): PhoneNumber = parseInternal(number, defaultRegion, libphonenumberCompat, keepRawInput = true)

    private fun parseInternal(
        number: String,
        defaultRegion: String,
        libphonenumberCompat: Boolean,
        keepRawInput: Boolean,
    ): PhoneNumber {
        if (number.length > MAX_INPUT_STRING_LENGTH) {
            throw NumberParseException(ErrorType.TOO_LONG, "The string supplied is too long to be a phone number")
        }
        // Resolve RFC3966 tel: URIs / phone-context, and trim leading junk, into the number to
        // parse. Throws NOT_A_NUMBER for an invalid phone-context descriptor.
        val built = buildNationalNumberForParsing(number)
        if (!isViablePhoneNumber(built)) {
            throw NumberParseException(ErrorType.NOT_A_NUMBER, "The string supplied did not seem to be a phone number")
        }
        // Split off a recognised extension (see stripExtension) before parsing the number itself;
        // the extension is carried on the result but never enters the E.164 form.
        val (main, extension) = stripExtension(built)
        var base = parseMain(main, defaultRegion, libphonenumberCompat, keepRawInput)
        // #6 Durchwahl guard: in the default (correct) mode only, a hyphen/space-separated trailing
        // group that upstream would silently fold into the national number is treated as ambiguous.
        // Compat mode keeps upstream's folding; a recognised extension means there is nothing to guard.
        if (!libphonenumberCompat && extension == null) {
            base = resolveDurchwahlAmbiguity(main, base)
        }
        if (extension != null) base = base.copy(extension = extension)
        if (keepRawInput) base = base.copy(rawInput = number)
        return base
    }

    /** Parse the number proper (extension already removed), mirroring libphonenumber's parseHelper. */
    private fun parseMain(number: String, defaultRegion: String, libphonenumberCompat: Boolean, keepRawInput: Boolean): PhoneNumber {
        val defaultMeta = GENERATED_METADATA[defaultRegion]

        // Trim any junk before the first "+" or decimal digit (libphonenumber's extractPossibleNumber),
        // so incidental leading words like "Tel:" are not read as alpha/vanity letters.
        val possible = extractPossibleNumber(number)
        // Detect a leading international "+" deterministically: scan code points for the first
        // ASCII/fullwidth plus or decimal digit — independent of the platform's Char.isWhitespace().
        val hasPlus = leadsWithPlus(possible)

        // checkRegionForParsing: a national-form number needs a known default region; a "+"-led number
        // carries its own calling code and parses even when the region is unknown (e.g. "ZZ").
        if (defaultMeta == null && !hasPlus) {
            throw NumberParseException(ErrorType.INVALID_COUNTRY_CODE, "Missing or invalid default region")
        }

        // A number with at least three letters is treated as an alpha (vanity) number: every letter
        // is mapped to its phone-keypad digit. Otherwise letters are dropped and only digits kept.
        // Deterministic on every target (frozen digit table; ASCII keypad).
        val digits = if (hasAtLeastThreeAlpha(possible)) {
            alphaConvertForParsing(possible, libphonenumberCompat)
        } else {
            normalizeDigitsOnly(possible, libphonenumberCompat)
        }
        if (digits.isEmpty()) throw NumberParseException(ErrorType.NOT_A_NUMBER, "No digits in input")

        // Extract the country calling code (maybeExtractCountryCode), with the plus-retry parseHelper
        // does when a "+"-led number produced an INVALID_COUNTRY_CODE: drop the "+" and re-extract,
        // treating the leading digits as an IDD instead.
        val extraction = try {
            maybeExtractCountryCode(digits, hasPlus, defaultMeta)
        } catch (e: NumberParseException) {
            if (e.errorType == ErrorType.INVALID_COUNTRY_CODE && hasPlus) {
                val retry = maybeExtractCountryCode(digits, false, defaultMeta)
                if (retry.countryCode == 0) {
                    throw NumberParseException(ErrorType.INVALID_COUNTRY_CODE, "Country calling code supplied was not recognised")
                }
                retry
            } else {
                throw e
            }
        }

        var countryCode = extraction.countryCode
        var nationalNumber = extraction.nationalNumber
        val meta: PhoneMetadata?
        if (countryCode != 0) {
            // Resolve the region for the extracted code; switch to its metadata when it isn't the
            // default region's (so national-prefix processing uses the right rules).
            val region = COUNTRY_CODE_TO_MAIN_REGION[countryCode]
            meta = if (region != null && region != defaultRegion) GENERATED_METADATA[region] else defaultMeta
        } else {
            // No country code found: the whole number is national, dialed from the default region.
            nationalNumber = digits
            meta = defaultMeta
            countryCode = defaultMeta?.countryCode ?: 0
        }

        // Strip the national prefix (and capture any carrier-selection code), but keep the stripped
        // result only if it is still a possible length for the region — otherwise the original was
        // likely a valid short number (libphonenumber's parseHelper does exactly this after
        // maybeStripNationalPrefixAndCarrierCode).
        var carrierCode = ""
        if (meta != null) {
            val strip = maybeStripNationalPrefixAndCarrier(nationalNumber, meta)
            when (testNumberLength(strip.number, meta, PhoneNumberType.UNKNOWN)) {
                ValidationResult.TOO_SHORT, ValidationResult.IS_POSSIBLE_LOCAL_ONLY, ValidationResult.INVALID_LENGTH -> {}
                else -> {
                    nationalNumber = strip.number
                    carrierCode = strip.carrierCode
                }
            }
        }
        var result = withItalianLeadingZeros(PhoneNumber(countryCode, nationalNumber))
        if (keepRawInput) {
            result = result.copy(countryCodeSource = extraction.source, preferredDomesticCarrierCode = carrierCode)
        }
        return validateLength(result)
    }

    /**
     * The Durchwahl (direct-dial) ambiguity guard, applied in default mode only (#6). German/Austrian
     * and similar numbers spell a direct-dial extension with a hyphen or space (`+49 30 12345678-12`);
     * upstream — and this port's compat mode — fold those digits into the national number, producing a
     * different, valid-looking number. Here, when the input carries a trailing digit group separated by
     * formatting, we ask whether the number is valid both **with** and **without** that group:
     *  - valid both ways → genuinely ambiguous → refuse (`NOT_A_NUMBER`) rather than invent a number;
     *  - valid only without the group → resolve to the base number, keeping the group as the extension;
     *  - otherwise → keep the folded number unchanged.
     *
     * This is a deliberate divergence from upstream (a variable-length dialling plan makes both readings
     * valid, which upstream does not reason about); fixed-length plans (US, GB) are unaffected because the
     * folded reading is invalid there. See ADR/issue #6.
     */
    private fun resolveDurchwahlAmbiguity(main: String, folded: PhoneNumber): PhoneNumber {
        val trailingCount = trailingGroupDigitCount(main)
        if (trailingCount <= 0) return folded
        val nsn = folded.nationalNumber
        if (trailingCount >= nsn.length) return folded // removing the group would leave nothing
        val base = withItalianLeadingZeros(PhoneNumber(folded.countryCode, nsn.dropLast(trailingCount)))
        val baseValid = isValidNumber(base)
        if (!baseValid) return folded // the base isn't a number on its own, so nothing is ambiguous
        if (isValidNumber(folded)) {
            throw NumberParseException(
                ErrorType.NOT_A_NUMBER,
                "Ambiguous trailing group: the number is valid both with and without it",
            )
        }
        // Folded form is invalid but the base is valid: the trailing group is the direct-dial extension.
        return base.copy(extension = nsn.takeLast(trailingCount))
    }

    /**
     * The number of digits in the final formatting-separated group of [main], or 0 when there is no
     * such group (fewer than two digit runs). Used by [resolveDurchwahlAmbiguity]; counts Unicode
     * decimal digits by code point so it is deterministic on every target.
     */
    private fun trailingGroupDigitCount(main: String): Int {
        val part = extractPossibleNumber(main)
        var runs = 0
        var lastRunLen = 0
        var inRun = false
        var i = 0
        while (i < part.length) {
            val c = part[i]
            val cp: Int
            if (c.isHighSurrogate() && i + 1 < part.length && part[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (part[i + 1].code - 0xDC00)
                i += 2
            } else {
                cp = c.code
                i += 1
            }
            if (decimalDigitValue(cp) != null) {
                if (!inRun) { inRun = true; runs++; lastRunLen = 0 }
                lastRunLen++
            } else {
                inRun = false
            }
        }
        return if (runs >= 2) lastRunLen else 0
    }

    /**
     * Set the italian-leading-zero flag and count, mirroring `setItalianLeadingZerosForPhoneNumber`.
     * This port also keeps the leading zeros in [PhoneNumber.nationalNumber] (so E.164 and
     * [getNationalSignificantNumber] are unaffected); the flag mirrors upstream for the helpers that
     * read it (e.g. [getLengthOfGeographicalAreaCode]).
     */
    private fun withItalianLeadingZeros(pn: PhoneNumber): PhoneNumber {
        val nn = pn.nationalNumber
        if (nn.length <= 1 || nn[0] != '0') return pn
        var zeros = 1
        while (zeros < nn.length - 1 && nn[zeros] == '0') zeros++
        return pn.copy(italianLeadingZero = true, numberOfLeadingZeros = zeros)
    }

    /**
     * Whether [input] looks enough like a phone number to attempt parsing, mirroring the intent of
     * libphonenumber's `isViablePhoneNumber` / `VALID_PHONE_NUMBER`: at least two characters, and
     * either exactly two decimal digits and nothing else, or at least three decimal digits.
     * Counts Unicode decimal digits by code point via the frozen table, so it is deterministic on
     * every target. (A simplification of upstream's full character-class check — it does not reject
     * an input that mixes in disallowed punctuation, which the subsequent digit extraction ignores.)
     */
    private fun isViablePhoneNumber(input: String): Boolean {
        if (input.length < MIN_LENGTH_FOR_NSN) return false
        var digitCount = 0
        var nonDigitCount = 0
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val cp: Int
            if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (input[i + 1].code - 0xDC00)
                i += 2
            } else {
                cp = c.code
                i += 1
            }
            if (decimalDigitValue(cp) != null) digitCount++ else nonDigitCount++
        }
        if (digitCount >= 3) return true
        return digitCount == MIN_LENGTH_FOR_NSN && nonDigitCount == 0
    }

    /** Bounds check on the national significant number, mirroring libphonenumber's parse tail. */
    private fun validateLength(pn: PhoneNumber): PhoneNumber {
        val len = pn.nationalNumber.length
        if (len < MIN_LENGTH_FOR_NSN) {
            throw NumberParseException(ErrorType.TOO_SHORT_NSN, "The number is too short to be a phone number")
        }
        if (len > MAX_LENGTH_FOR_NSN) {
            throw NumberParseException(ErrorType.TOO_LONG, "The number is too long to be a phone number")
        }
        return pn
    }

    /**
     * True if [number], parsed for [defaultRegion], is a valid number — i.e.
     * libphonenumber's getNumberType would return a known type. Resolves the
     * region among those sharing the calling code, then requires the national
     * number to match the general pattern and at least one specific type pattern.
     */
    public fun isValid(
        number: String,
        defaultRegion: String,
        libphonenumberCompat: Boolean = false,
    ): Boolean = try {
        val pn = parse(number, defaultRegion, libphonenumberCompat)
        val region = getRegionForNumber(pn.countryCode, pn.nationalNumber)
        val meta = region?.let { GENERATED_METADATA[it] }
        meta != null && isValidForRegion(pn.nationalNumber, meta)
    } catch (e: NumberParseException) {
        false
    }

    /**
     * Resolve the region among those sharing a calling code, mirroring
     * libphonenumber's getRegionCodeForNumber: a region's leadingDigits (if any)
     * disambiguates by prefix; otherwise a region claims the number if the number
     * is valid there. Regions are tried main-first.
     */
    private fun getRegionForNumber(countryCode: Int, nsn: String): String? {
        val regions = COUNTRY_CODE_TO_REGIONS[countryCode] ?: return null
        if (regions.size == 1) return regions[0]
        for (region in regions) {
            val meta = GENERATED_METADATA[region] ?: continue
            val leading = meta.leadingDigits
            if (leading != null) {
                if (compiled(leading).matchAtStart(nsn) != null) return region
            } else if (isValidForRegion(nsn, meta)) {
                return region
            }
        }
        return null
    }

    /**
     * True if [nsn] is a valid number for [meta]'s region — i.e. libphonenumber's getNumberType
     * would return a known type.
     */
    private fun isValidForRegion(nsn: String, meta: PhoneMetadata): Boolean =
        getNumberTypeHelper(nsn, meta) != PhoneNumberType.UNKNOWN

    /**
     * Port of libphonenumber's `getNumberTypeHelper`: classify [nsn] against [meta]'s per-type
     * national-number patterns, in upstream's exact order. Requires the general pattern to match
     * first; a type matches only when that type's pattern exists and fully matches. Fixed-line and
     * mobile are merged to [PhoneNumberType.FIXED_LINE_OR_MOBILE] when their patterns coincide.
     */
    private fun getNumberTypeHelper(nsn: String, meta: PhoneMetadata): PhoneNumberType {
        if (!compiled(meta.generalNationalNumberPattern).matches(nsn)) return PhoneNumberType.UNKNOWN
        fun matches(key: String) = meta.typePatterns[key]?.let { compiled(it).matches(nsn) } == true
        if (matches("premiumRate")) return PhoneNumberType.PREMIUM_RATE
        if (matches("tollFree")) return PhoneNumberType.TOLL_FREE
        if (matches("sharedCost")) return PhoneNumberType.SHARED_COST
        if (matches("voip")) return PhoneNumberType.VOIP
        if (matches("personalNumber")) return PhoneNumberType.PERSONAL_NUMBER
        if (matches("pager")) return PhoneNumberType.PAGER
        if (matches("uan")) return PhoneNumberType.UAN
        if (matches("voicemail")) return PhoneNumberType.VOICEMAIL
        val sameMobileAndFixed = sameMobileAndFixedLinePattern(meta)
        if (matches("fixedLine")) {
            return if (sameMobileAndFixed || matches("mobile")) PhoneNumberType.FIXED_LINE_OR_MOBILE else PhoneNumberType.FIXED_LINE
        }
        if (!sameMobileAndFixed && matches("mobile")) return PhoneNumberType.MOBILE
        return PhoneNumberType.UNKNOWN
    }

    /** Whether the region's mobile and fixed-line national-number patterns coincide. */
    private fun sameMobileAndFixedLinePattern(meta: PhoneMetadata): Boolean {
        val fixed = meta.typePatterns["fixedLine"] ?: return false
        return fixed == meta.typePatterns["mobile"]
    }

    /** Number types, mirroring libphonenumber's `PhoneNumberType`. */
    public enum class PhoneNumberType {
        FIXED_LINE, MOBILE, FIXED_LINE_OR_MOBILE, TOLL_FREE, PREMIUM_RATE,
        SHARED_COST, VOIP, PERSONAL_NUMBER, PAGER, UAN, VOICEMAIL, UNKNOWN,
    }

    /** Output formats, mirroring libphonenumber's `PhoneNumberFormat`. */
    public enum class PhoneNumberFormat { E164, INTERNATIONAL, NATIONAL, RFC3966 }

    /** Result of comparing two numbers with [isNumberMatch], mirroring libphonenumber's `MatchType`. */
    public enum class MatchType { NOT_A_NUMBER, NO_MATCH, SHORT_NSN_MATCH, NSN_MATCH, EXACT_MATCH }

    /** Result of a possible-length check, mirroring libphonenumber's `ValidationResult`. */
    public enum class ValidationResult {
        IS_POSSIBLE, IS_POSSIBLE_LOCAL_ONLY, INVALID_COUNTRY_CODE, TOO_SHORT, INVALID_LENGTH, TOO_LONG,
    }

    private fun typeKey(type: PhoneNumberType): String? = when (type) {
        PhoneNumberType.FIXED_LINE -> "fixedLine"
        PhoneNumberType.MOBILE -> "mobile"
        PhoneNumberType.TOLL_FREE -> "tollFree"
        PhoneNumberType.PREMIUM_RATE -> "premiumRate"
        PhoneNumberType.SHARED_COST -> "sharedCost"
        PhoneNumberType.VOIP -> "voip"
        PhoneNumberType.PERSONAL_NUMBER -> "personalNumber"
        PhoneNumberType.PAGER -> "pager"
        PhoneNumberType.UAN -> "uan"
        PhoneNumberType.VOICEMAIL -> "voicemail"
        else -> null
    }

    /**
     * Port of libphonenumber's `testNumberLength`: classify [nsn]'s length against [meta]'s
     * possible-length tables for [type] (see [ValidationResult]).
     */
    private fun testNumberLength(nsn: String, meta: PhoneMetadata, type: PhoneNumberType): ValidationResult {
        val possible: List<Int>
        var local: List<Int>
        when (type) {
            PhoneNumberType.FIXED_LINE_OR_MOBILE -> {
                if ("fixedLine" !in meta.typePatterns) {
                    return testNumberLength(nsn, meta, PhoneNumberType.MOBILE)
                }
                var merged = meta.typePossibleLengths["fixedLine"] ?: meta.possibleLengths
                local = meta.typePossibleLengthsLocalOnly["fixedLine"] ?: emptyList()
                if ("mobile" in meta.typePatterns) {
                    val mob = meta.typePossibleLengths["mobile"] ?: meta.possibleLengths
                    merged = (merged + mob).distinct().sorted()
                    val mobLocal = meta.typePossibleLengthsLocalOnly["mobile"] ?: emptyList()
                    local = if (local.isEmpty()) mobLocal else (local + mobLocal).distinct().sorted()
                }
                possible = merged
            }
            PhoneNumberType.UNKNOWN -> {
                possible = meta.possibleLengths
                local = meta.possibleLengthsLocalOnly
            }
            else -> {
                val key = typeKey(type)
                if (key == null || key !in meta.typePatterns) return ValidationResult.INVALID_LENGTH
                possible = meta.typePossibleLengths[key] ?: meta.possibleLengths
                local = meta.typePossibleLengthsLocalOnly[key] ?: emptyList()
            }
        }
        if (possible.isEmpty() || possible[0] == -1) return ValidationResult.INVALID_LENGTH
        val actual = nsn.length
        if (actual in local) return ValidationResult.IS_POSSIBLE_LOCAL_ONLY
        val min = possible[0]
        return when {
            min == actual -> ValidationResult.IS_POSSIBLE
            min > actual -> ValidationResult.TOO_SHORT
            possible.last() < actual -> ValidationResult.TOO_LONG
            actual in possible.subList(1, possible.size) -> ValidationResult.IS_POSSIBLE
            else -> ValidationResult.INVALID_LENGTH
        }
    }

    private fun metadataForNumber(number: PhoneNumber): PhoneMetadata? =
        COUNTRY_CODE_TO_MAIN_REGION[number.countryCode]?.let { GENERATED_METADATA[it] }

    // --- Number type & validity (#15) ---

    /**
     * The type of [number], mirroring libphonenumber's `getNumberType`. Resolves the region for the
     * number (among those sharing its calling code) and classifies the national number against that
     * region's per-type patterns; returns [PhoneNumberType.UNKNOWN] when no type matches.
     */
    public fun getNumberType(number: PhoneNumber): PhoneNumberType {
        val region = getRegionForNumber(number.countryCode, number.nationalNumber) ?: return PhoneNumberType.UNKNOWN
        val meta = GENERATED_METADATA[region] ?: return PhoneNumberType.UNKNOWN
        return getNumberTypeHelper(getNationalSignificantNumber(number), meta)
    }

    /**
     * True if [number] is a valid number — i.e. [getNumberType] returns a known type. Mirrors
     * libphonenumber's `isValidNumber`.
     */
    public fun isValidNumber(number: PhoneNumber): Boolean {
        val region = getRegionForNumber(number.countryCode, number.nationalNumber) ?: return false
        val meta = GENERATED_METADATA[region] ?: return false
        return isValidForRegion(number.nationalNumber, meta)
    }

    /**
     * True if [number] is valid for [regionCode] specifically, mirroring `isValidNumberForRegion`:
     * the region must carry the number's calling code (unless it is the non-geographical entity),
     * and the national number must match a known type there.
     */
    public fun isValidNumberForRegion(number: PhoneNumber, regionCode: String): Boolean {
        val meta = getMetadataForRegionOrCallingCode(number.countryCode, regionCode) ?: return false
        if (regionCode != REGION_CODE_FOR_NON_GEO_ENTITY && number.countryCode != meta.countryCode) return false
        return getNumberTypeHelper(getNationalSignificantNumber(number), meta) != PhoneNumberType.UNKNOWN
    }

    /** Number types that exist for [regionCode], mirroring `getSupportedTypesForRegion`. */
    public fun getSupportedTypesForRegion(regionCode: String): Set<PhoneNumberType> {
        if (!isValidRegionCode(regionCode)) return emptySet()
        return supportedTypes(GENERATED_METADATA[regionCode]!!)
    }

    /** Number types that exist for the non-geographical entity [countryCallingCode]. */
    public fun getSupportedTypesForNonGeoEntity(countryCallingCode: Int): Set<PhoneNumberType> {
        val meta = metadataForNonGeoEntity(countryCallingCode) ?: return emptySet()
        return supportedTypes(meta)
    }

    /** Port of `getSupportedTypesForMetadata`: every concrete type that has data (never OR / UNKNOWN). */
    private fun supportedTypes(meta: PhoneMetadata): Set<PhoneNumberType> {
        val out = LinkedHashSet<PhoneNumberType>()
        for (type in PhoneNumberType.values()) {
            if (type == PhoneNumberType.FIXED_LINE_OR_MOBILE || type == PhoneNumberType.UNKNOWN) continue
            val key = typeKey(type) ?: continue
            if (descHasData(meta, key)) out.add(type)
        }
        return out
    }

    /** Port of `descHasData`: a type has data when it carries a pattern or real possible-length data. */
    private fun descHasData(meta: PhoneMetadata, key: String): Boolean {
        if (meta.typePatterns[key] != null) return true
        val lengths = meta.typePossibleLengths[key] ?: return false
        return !(lengths.size == 1 && lengths[0] == -1)
    }

    private fun getMetadataForRegionOrCallingCode(cc: Int, regionCode: String?): PhoneMetadata? =
        if (regionCode == REGION_CODE_FOR_NON_GEO_ENTITY) metadataForNonGeoEntity(cc)
        else regionCode?.let { GENERATED_METADATA[it] }

    /** Non-geographical metadata for [cc] (id "001"), or null when [cc] is a geographic calling code. */
    private fun metadataForNonGeoEntity(cc: Int): PhoneMetadata? =
        metadataForCountryCode(cc)?.takeIf { it.id == REGION_CODE_FOR_NON_GEO_ENTITY }

    // --- Accessors & helpers (#19) ---

    private const val UNKNOWN_REGION = "ZZ"
    // Rough heuristics from libphonenumber: which geographic-mobile calling codes have area codes,
    // and the mobile token that forms part of the NSN for some of them.
    private val GEO_MOBILE_COUNTRIES_WITHOUT_MOBILE_AREA_CODES = setOf(86) // China
    private val GEO_MOBILE_COUNTRIES = setOf(52, 54, 55, 62) // Mexico, Argentina, Brazil, Indonesia
    private val MOBILE_TOKEN_MAPPINGS = mapOf(54 to "9") // Argentina
    // Countries that have area codes even though they use no national prefix (upstream's set).
    private val COUNTRIES_WITHOUT_NATIONAL_PREFIX_WITH_AREA_CODES = setOf(52) // Mexico

    /** All supported geographic region codes (excludes the non-geographical entity). */
    public fun getSupportedRegions(): Set<String> =
        GENERATED_METADATA.values.mapNotNull { if (it.id != REGION_CODE_FOR_NON_GEO_ENTITY) it.id else null }.toSet()

    /** All supported country calling codes (geographic and non-geographical). */
    public fun getSupportedCallingCodes(): Set<Int> = COUNTRY_CODE_TO_MAIN_REGION.keys.toSet()

    /** The calling codes served by non-geographical entities (e.g. 800, 808, 870). */
    public fun getSupportedGlobalNetworkCallingCodes(): Set<Int> =
        COUNTRY_CODE_TO_MAIN_REGION.entries
            .filter { GENERATED_METADATA[it.value]?.id == REGION_CODE_FOR_NON_GEO_ENTITY }
            .map { it.key }.toSet()

    /** The calling code for [regionCode], or 0 if the region is unknown/non-geographical. */
    public fun getCountryCodeForRegion(regionCode: String): Int =
        if (!isValidRegionCode(regionCode)) 0 else GENERATED_METADATA[regionCode]!!.countryCode

    /** The main region for [countryCallingCode], "001" for a non-geo code, or "ZZ" if unknown. */
    public fun getRegionCodeForCountryCode(countryCallingCode: Int): String {
        val key = COUNTRY_CODE_TO_MAIN_REGION[countryCallingCode] ?: return UNKNOWN_REGION
        return GENERATED_METADATA[key]?.id ?: UNKNOWN_REGION
    }

    /** All region codes sharing [countryCallingCode], main region first; empty if unknown. */
    public fun getRegionCodesForCountryCode(countryCallingCode: Int): List<String> =
        COUNTRY_CODE_TO_REGIONS[countryCallingCode]?.map { GENERATED_METADATA[it]?.id ?: it } ?: emptyList()

    /** The region [number] belongs to (id, or "001" for a non-geo entity), or null if undetermined. */
    public fun getRegionCodeForNumber(number: PhoneNumber): String? {
        val key = getRegionForNumber(number.countryCode, number.nationalNumber) ?: return null
        return GENERATED_METADATA[key]?.id ?: key
    }

    /** The national (trunk) dialling prefix for [regionCode], stripping "~" pauses if asked; or null. */
    public fun getNddPrefixForRegion(regionCode: String, stripNonDigits: Boolean): String? {
        val meta = getMetadataForRegion(regionCode) ?: return null
        val np = meta.nationalPrefix
        if (np.isNullOrEmpty()) return null
        return if (stripNonDigits) np.replace("~", "") else np
    }

    /** The mobile token that forms part of the NSN for [countryCallingCode] (e.g. "9" for Argentina), or "". */
    public fun getCountryMobileToken(countryCallingCode: Int): String =
        MOBILE_TOKEN_MAPPINGS[countryCallingCode] ?: ""

    /** True if [regionCode] belongs to the North American Numbering Plan (calling code 1). */
    public fun isNANPACountry(regionCode: String): Boolean = isNanpaCountry(regionCode)

    /** Whether [number] is a geographic number (fixed-line / fixed-or-mobile, or a geo-mobile country). */
    public fun isNumberGeographical(number: PhoneNumber): Boolean =
        isNumberGeographical(getNumberType(number), number.countryCode)

    /** Whether a number of [phoneNumberType] on [countryCallingCode] is geographic. */
    public fun isNumberGeographical(phoneNumberType: PhoneNumberType, countryCallingCode: Int): Boolean =
        phoneNumberType == PhoneNumberType.FIXED_LINE ||
            phoneNumberType == PhoneNumberType.FIXED_LINE_OR_MOBILE ||
            (countryCallingCode in GEO_MOBILE_COUNTRIES && phoneNumberType == PhoneNumberType.MOBILE)

    /** True if [number] contains three or more letters (a vanity number), mirroring `isAlphaNumber`. */
    public fun isAlphaNumber(number: String): Boolean {
        if (!isViablePhoneNumber(number)) return false
        val (main, _) = stripExtension(number)
        return hasAtLeastThreeAlpha(main)
    }

    /** Whether [number] can be dialled from outside its region, mirroring `canBeInternationallyDialled`. */
    public fun canBeInternationallyDialled(number: PhoneNumber): Boolean {
        val meta = getMetadataForRegion(getRegionCodeForNumber(number)) ?: return true
        val pattern = meta.noInternationalDialling ?: return true
        return !compiled(pattern).matches(getNationalSignificantNumber(number))
    }

    /** Whether [regionCode] supports mobile number portability. */
    public fun isMobileNumberPortableRegion(regionCode: String): Boolean =
        getMetadataForRegion(regionCode)?.mobileNumberPortableRegion ?: false

    /** The length of the geographical area code of [number], or 0 when it has none. */
    public fun getLengthOfGeographicalAreaCode(number: PhoneNumber): Int {
        val meta = getMetadataForRegion(getRegionCodeForNumber(number)) ?: return 0
        // A closed dialling plan (no national prefix) with no italian leading zero has no area codes,
        // except for the few countries that keep area codes without a national prefix (e.g. Mexico).
        if (meta.nationalPrefix.isNullOrEmpty() && !number.italianLeadingZero &&
            number.countryCode !in COUNTRIES_WITHOUT_NATIONAL_PREFIX_WITH_AREA_CODES
        ) {
            return 0
        }
        val type = getNumberType(number)
        val cc = number.countryCode
        if (type == PhoneNumberType.MOBILE && cc in GEO_MOBILE_COUNTRIES_WITHOUT_MOBILE_AREA_CODES) return 0
        if (!isNumberGeographical(type, cc)) return 0
        return getLengthOfNationalDestinationCode(number)
    }

    /** The length of the national destination code (NDC) of [number], mirroring the upstream heuristic. */
    public fun getLengthOfNationalDestinationCode(number: PhoneNumber): Int {
        val copied = if (number.extension != null) number.copy(extension = null) else number
        val groups = splitOnNonDigits(format(copied, PhoneNumberFormat.INTERNATIONAL))
        // groups[0] = "" (before "+"), [1] = country code, [2] = NDC when it isn't the last group.
        if (groups.size <= 3) return 0
        if (getNumberType(number) == PhoneNumberType.MOBILE && getCountryMobileToken(number.countryCode) != "") {
            // e.g. Argentina: +54 9 NDC XXXX — the mobile token (group 2) is part of the NSN.
            return groups[2].length + groups[3].length
        }
        return groups[2].length
    }

    /** Split on maximal non-digit runs, matching Java's `"\\D+".split` (leading "" kept, trailing dropped). */
    private fun splitOnNonDigits(s: String): List<String> {
        val runs = mutableListOf<String>()
        val cur = StringBuilder()
        for (c in s) {
            if (c in '0'..'9') {
                cur.append(c)
            } else if (cur.isNotEmpty()) {
                runs.add(cur.toString()); cur.clear()
            }
        }
        if (cur.isNotEmpty()) runs.add(cur.toString())
        return if (s.isNotEmpty() && s[0] !in '0'..'9') listOf("") + runs else runs
    }

    // --- Example numbers (#19) ---

    /** An example valid number for [regionCode] and [type], parsed from the embedded example; or null. */
    public fun getExampleNumberForType(regionCode: String, type: PhoneNumberType): PhoneNumber? {
        if (!isValidRegionCode(regionCode)) return null
        val meta = GENERATED_METADATA[regionCode] ?: return null
        val key = exampleTypeKey(type) ?: return null
        val example = meta.typeExampleNumbers[key] ?: return null
        return try { parse(example, regionCode) } catch (e: NumberParseException) { null }
    }

    // Which type descriptor supplies the example for a requested type. Fixed-or-mobile uses the
    // fixed-line desc, matching upstream's getNumberDescByType.
    private fun exampleTypeKey(type: PhoneNumberType): String? =
        if (type == PhoneNumberType.FIXED_LINE_OR_MOBILE) "fixedLine" else typeKey(type)

    /** An example fixed-line number for [regionCode], or null. Mirrors `getExampleNumber`. */
    public fun getExampleNumber(regionCode: String): PhoneNumber? =
        getExampleNumberForType(regionCode, PhoneNumberType.FIXED_LINE)

    /** An example number for the non-geographical entity [countryCallingCode], or null. */
    public fun getExampleNumberForNonGeoEntity(countryCallingCode: Int): PhoneNumber? {
        val meta = metadataForNonGeoEntity(countryCallingCode) ?: return null
        for (key in listOf("mobile", "tollFree", "sharedCost", "voip", "voicemail", "uan", "premiumRate")) {
            val example = meta.typeExampleNumbers[key] ?: continue
            try {
                return parse("+$countryCallingCode$example", UNKNOWN_REGION)
            } catch (e: NumberParseException) {
            }
        }
        return null
    }

    /**
     * An example number of [type] with no region specified, trying every supported region and then
     * the non-geographical entities. Mirrors upstream's single-argument `getExampleNumberForType`.
     */
    public fun getExampleNumberForType(type: PhoneNumberType): PhoneNumber? {
        for (region in getSupportedRegions()) {
            getExampleNumberForType(region, type)?.let { return it }
        }
        val key = exampleTypeKey(type) ?: return null
        for (cc in getSupportedGlobalNetworkCallingCodes()) {
            val meta = metadataForNonGeoEntity(cc) ?: continue
            val example = meta.typeExampleNumbers[key] ?: continue
            try {
                return parse("+$cc$example", UNKNOWN_REGION)
            } catch (e: NumberParseException) {
            }
        }
        return null
    }

    /**
     * An example invalid number for [regionCode]: the fixed-line example truncated until it is no
     * longer valid. Mirrors `getInvalidExampleNumber`.
     */
    public fun getInvalidExampleNumber(regionCode: String): PhoneNumber? {
        if (!isValidRegionCode(regionCode)) return null
        val meta = GENERATED_METADATA[regionCode] ?: return null
        val example = meta.typeExampleNumbers["fixedLine"] ?: return null
        for (len in example.length - 1 downTo MIN_LENGTH_FOR_NSN) {
            val candidate = example.substring(0, len)
            try {
                val pn = parse(candidate, regionCode)
                if (!isValidNumber(pn)) return pn
            } catch (e: NumberParseException) {
            }
        }
        return null
    }

    // --- Public normalize / convert helpers (#19; ties to #9) ---

    /**
     * Convert the letters in [number] to their phone-keypad digits, keeping every other character
     * (digits and punctuation) as-is. Mirrors `convertAlphaCharactersInNumber` (ASCII).
     */
    public fun convertAlphaCharactersInNumber(number: String): String {
        val sb = StringBuilder(number.length)
        for (c in number) {
            val d = keypadDigit(c)
            sb.append(d ?: c)
        }
        return sb.toString()
    }

    /** Keep only diallable characters (`0`-`9`, `+`, `*`, `#`), mirroring `normalizeDiallableCharsOnly`. */
    public fun normalizeDiallableCharsOnly(number: String): String {
        val sb = StringBuilder(number.length)
        for (c in number) {
            if (c in '0'..'9' || c == '+' || c == '*' || c == '#') sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Normalize [number] as libphonenumber's internal `normalize`: an alpha number (three or more
     * letters) is keypad-converted keeping only mapped characters; otherwise only decimal digits are
     * kept. Internal — exposed for parity testing; callers use [normalizeDigitsOnly] /
     * [convertAlphaCharactersInNumber] / [normalizeDiallableCharsOnly].
     */
    internal fun normalize(number: String): String =
        if (hasAtLeastThreeAlpha(number)) alphaConvertForParsing(number, libphonenumberCompat = false)
        else normalizeDigitsOnly(number, libphonenumberCompat = false)

    // --- isNumberMatch (#17) ---

    /**
     * Compare two parsed numbers for equivalence, mirroring libphonenumber's `isNumberMatch`:
     * [MatchType.EXACT_MATCH] when country code and national number (and extension) agree,
     * [MatchType.NSN_MATCH] when they agree once a missing country code is filled in,
     * [MatchType.SHORT_NSN_MATCH] when one national number is a suffix of the other (covering
     * italian-leading-zero and extension differences), else [MatchType.NO_MATCH].
     *
     * Raw input, country-code source and carrier code are ignored (as upstream clears them first).
     * This port keeps leading zeros in [PhoneNumber.nationalNumber]; the comparison uses the
     * zero-stripped "core" national number plus the leading-zero flags, matching upstream's long-based
     * model.
     */
    public fun isNumberMatch(first: PhoneNumber, second: PhoneNumber): MatchType {
        val aExt = first.extension?.ifEmpty { null }
        val bExt = second.extension?.ifEmpty { null }
        // Early exit if both had extensions and they differ.
        if (aExt != null && bExt != null && aExt != bExt) return MatchType.NO_MATCH

        val ccA = first.countryCode
        val ccB = second.countryCode
        if (ccA != 0 && ccB != 0) {
            if (exactlySameForMatch(first, aExt, second, bExt)) return MatchType.EXACT_MATCH
            if (ccA == ccB && isNationalNumberSuffixOfTheOther(first, second)) return MatchType.SHORT_NSN_MATCH
            return MatchType.NO_MATCH
        }
        // One or both country codes unspecified: align them, then an exact match is an NSN match.
        val aAligned = first.copy(countryCode = ccB)
        if (exactlySameForMatch(aAligned, aExt, second, bExt)) return MatchType.NSN_MATCH
        if (isNationalNumberSuffixOfTheOther(aAligned, second)) return MatchType.SHORT_NSN_MATCH
        return MatchType.NO_MATCH
    }

    /**
     * Compare a parsed number to a string, mirroring the `(PhoneNumber, CharSequence)` overload: the
     * string is parsed with its own country code if it has one, else parsed as [first]'s region (an
     * exact result then downgrades to [MatchType.NSN_MATCH]).
     */
    public fun isNumberMatch(first: PhoneNumber, second: String): MatchType {
        val secondProto = try {
            parse(second, UNKNOWN_REGION)
        } catch (e: NumberParseException) {
            if (e.errorType != ErrorType.INVALID_COUNTRY_CODE) return MatchType.NOT_A_NUMBER
            val firstRegion = getRegionCodeForCountryCode(first.countryCode)
            return try {
                if (firstRegion != UNKNOWN_REGION) {
                    val withRegion = parse(second, firstRegion)
                    val match = isNumberMatch(first, withRegion)
                    if (match == MatchType.EXACT_MATCH) MatchType.NSN_MATCH else match
                } else {
                    isNumberMatch(first, parseForMatch(second))
                }
            } catch (e2: NumberParseException) {
                MatchType.NOT_A_NUMBER
            }
        }
        return isNumberMatch(first, secondProto)
    }

    /**
     * Compare two strings, mirroring the `(CharSequence, CharSequence)` overload: each is parsed with
     * its own country code where possible, falling back to region-less parsing when neither carries one.
     */
    public fun isNumberMatch(first: String, second: String): MatchType {
        val firstProto = try {
            parse(first, UNKNOWN_REGION)
        } catch (e: NumberParseException) {
            if (e.errorType != ErrorType.INVALID_COUNTRY_CODE) return MatchType.NOT_A_NUMBER
            val secondProto = try {
                parse(second, UNKNOWN_REGION)
            } catch (e2: NumberParseException) {
                if (e2.errorType != ErrorType.INVALID_COUNTRY_CODE) return MatchType.NOT_A_NUMBER
                return try {
                    isNumberMatch(parseForMatch(first), parseForMatch(second))
                } catch (e3: NumberParseException) {
                    MatchType.NOT_A_NUMBER
                }
            }
            return isNumberMatch(secondProto, first)
        }
        return isNumberMatch(firstProto, second)
    }

    /**
     * The zero-stripped national number, mirroring upstream's long `nationalNumber` (which never
     * carries leading zeros — those live in the italian-leading-zero flag/count). Keeps at least one
     * digit.
     */
    private fun coreNsn(pn: PhoneNumber): String {
        val s = pn.nationalNumber
        var i = 0
        while (i < s.length - 1 && s[i] == '0') i++
        return s.substring(i)
    }

    /** Whether the numbers agree on country code, core NSN, extension and leading-zero metadata. */
    private fun exactlySameForMatch(a: PhoneNumber, aExt: String?, b: PhoneNumber, bExt: String?): Boolean =
        a.countryCode == b.countryCode &&
            coreNsn(a) == coreNsn(b) &&
            aExt == bExt &&
            a.italianLeadingZero == b.italianLeadingZero &&
            a.numberOfLeadingZeros == b.numberOfLeadingZeros

    /** Whether one core national number is a suffix of the other (endsWith, so equality counts). */
    private fun isNationalNumberSuffixOfTheOther(a: PhoneNumber, b: PhoneNumber): Boolean {
        val na = coreNsn(a)
        val nb = coreNsn(b)
        return na.endsWith(nb) || nb.endsWith(na)
    }

    /**
     * Parse [number] with no region and no region check, mirroring `parseHelper(number, null, false,
     * false)`: extract a leading "+"/IDD country code if present, otherwise keep the digits as a
     * country-code-less national number. Used only by [isNumberMatch]'s region-less fallback.
     */
    private fun parseForMatch(number: String): PhoneNumber {
        if (number.length > MAX_INPUT_STRING_LENGTH) {
            throw NumberParseException(ErrorType.TOO_LONG, "The string supplied is too long to be a phone number")
        }
        val built = buildNationalNumberForParsing(number)
        if (!isViablePhoneNumber(built)) {
            throw NumberParseException(ErrorType.NOT_A_NUMBER, "The string supplied did not seem to be a phone number")
        }
        val (main, extension) = stripExtension(built)
        val possible = extractPossibleNumber(main)
        val hasPlus = leadsWithPlus(possible)
        val digits = if (hasAtLeastThreeAlpha(possible)) {
            alphaConvertForParsing(possible, libphonenumberCompat = false)
        } else {
            normalizeDigitsOnly(possible, libphonenumberCompat = false)
        }
        if (digits.isEmpty()) throw NumberParseException(ErrorType.NOT_A_NUMBER, "No digits in input")
        val extraction = maybeExtractCountryCode(digits, hasPlus, meta = null)
        val nsn = if (extraction.countryCode != 0) extraction.nationalNumber else digits
        val base = validateLength(withItalianLeadingZeros(PhoneNumber(extraction.countryCode, nsn)))
        return if (extension != null) base.copy(extension = extension) else base
    }

    /**
     * Whether [number] is a possible number, and why — matches libphonenumber's
     * `isPossibleNumberWithReason`. Checks length only, not full validity.
     */
    public fun isPossibleNumberWithReason(number: PhoneNumber): ValidationResult {
        val meta = metadataForNumber(number) ?: return ValidationResult.INVALID_COUNTRY_CODE
        return testNumberLength(number.nationalNumber, meta, PhoneNumberType.UNKNOWN)
    }

    /** Whether [number] is a possible number for [type], and why. */
    public fun isPossibleNumberForTypeWithReason(number: PhoneNumber, type: PhoneNumberType): ValidationResult {
        val meta = metadataForNumber(number) ?: return ValidationResult.INVALID_COUNTRY_CODE
        return testNumberLength(number.nationalNumber, meta, type)
    }

    /** True if [number] is a possible number (any acceptable length, including local-only). */
    public fun isPossibleNumber(number: PhoneNumber): Boolean = when (isPossibleNumberWithReason(number)) {
        ValidationResult.IS_POSSIBLE, ValidationResult.IS_POSSIBLE_LOCAL_ONLY -> true
        else -> false
    }

    /** True if [number] is a possible number for [type]. */
    public fun isPossibleNumberForType(number: PhoneNumber, type: PhoneNumberType): Boolean =
        when (isPossibleNumberForTypeWithReason(number, type)) {
            ValidationResult.IS_POSSIBLE, ValidationResult.IS_POSSIBLE_LOCAL_ONLY -> true
            else -> false
        }

    /**
     * If [number] is longer than the longest valid number for its region, drop trailing digits
     * until it is valid and return that; returns [number] unchanged when already valid, or null
     * when no truncation yields a valid number. Mirrors `truncateTooLongNumber` (immutable form).
     */
    public fun truncateTooLongNumber(number: PhoneNumber): PhoneNumber? {
        if (isValidNumber(number)) return number
        var nsn = number.nationalNumber
        while (true) {
            nsn = if (nsn.isEmpty()) "" else nsn.dropLast(1)
            if (nsn.isEmpty()) return null
            val copy = PhoneNumber(number.countryCode, nsn)
            if (isPossibleNumberWithReason(copy) == ValidationResult.TOO_SHORT) return null
            if (isValidNumber(copy)) return copy
        }
    }

    // --- Formatting (#14) ---

    // Extension prefixes used when appending a formatted extension (libphonenumber's
    // RFC3966_EXTN_PREFIX / DEFAULT_EXTN_PREFIX).
    private const val RFC3966_EXTN_PREFIX = ";ext="
    private const val DEFAULT_EXTN_PREFIX = " ext. "
    private const val CARRIER_CODE_PLACEHOLDER = "\$CC"

    /**
     * Format [number] in the requested [numberFormat], mirroring libphonenumber's `format`. E164 is
     * "+" + country code + national number; NATIONAL / INTERNATIONAL / RFC3966 apply the region's
     * display templates. A recognised extension is appended (except in E164, which has no slot for
     * one). When the country calling code is not recognised, the national number is returned
     * unformatted, exactly as upstream.
     */
    public fun format(number: PhoneNumber, numberFormat: PhoneNumberFormat): String {
        val nsn = getNationalSignificantNumber(number)
        val cc = number.countryCode
        if (numberFormat == PhoneNumberFormat.E164) {
            return prefixNumberWithCountryCallingCode(cc, PhoneNumberFormat.E164, nsn)
        }
        if (cc !in COUNTRY_CODE_TO_MAIN_REGION) return nsn
        val meta = metadataForCountryCode(cc) ?: return nsn
        val formattedNsn = formatNsn(nsn, meta, numberFormat, carrierCode = null)
        val withExtension = maybeAppendFormattedExtension(number, meta, numberFormat, formattedNsn)
        return prefixNumberWithCountryCallingCode(cc, numberFormat, withExtension)
    }

    private const val NANPA_COUNTRY_CODE = 1
    private const val REGION_CODE_FOR_NON_GEO_ENTITY = "001"
    // libphonenumber's SINGLE_INTERNATIONAL_PREFIX: an IDD that is a single, unambiguous prefix
    // (digits, optionally followed by a "~"-style pause then more digits) is safe to show verbatim.
    private const val SINGLE_INTERNATIONAL_PREFIX = "[\\d]+(?:[~⁓∼～][\\d]+)?"

    // A valid (geographic) region has metadata whose id is a real region, not the non-geo entity.
    // (The metadata map is keyed by region id for geographic regions; non-geo entries carry id "001".)
    private fun isValidRegionCode(region: String): Boolean =
        GENERATED_METADATA[region]?.let { it.id != REGION_CODE_FOR_NON_GEO_ENTITY } == true

    /** Metadata for a geographic region only (null for the non-geo entity "001" or an unknown region). */
    private fun getMetadataForRegion(regionCode: String?): PhoneMetadata? =
        if (regionCode == null || !isValidRegionCode(regionCode)) null else GENERATED_METADATA[regionCode]

    private fun isNanpaCountry(region: String): Boolean =
        region in (COUNTRY_CODE_TO_REGIONS[NANPA_COUNTRY_CODE] ?: emptyList())

    /**
     * Format [number] in NATIONAL form with a carrier selection code, mirroring
     * `formatNationalNumberWithCarrierCode`. The carrier code is inserted per the region's
     * carrier-code formatting rule; a region with no such rule ignores it.
     */
    public fun formatNationalNumberWithCarrierCode(number: PhoneNumber, carrierCode: String): String {
        val cc = number.countryCode
        val nsn = getNationalSignificantNumber(number)
        if (cc !in COUNTRY_CODE_TO_MAIN_REGION) return nsn
        val meta = metadataForCountryCode(cc) ?: return nsn
        val formattedNsn = formatNsn(nsn, meta, PhoneNumberFormat.NATIONAL, carrierCode)
        val withExt = maybeAppendFormattedExtension(number, meta, PhoneNumberFormat.NATIONAL, formattedNsn)
        return prefixNumberWithCountryCallingCode(cc, PhoneNumberFormat.NATIONAL, withExt)
    }

    /**
     * As [formatNationalNumberWithCarrierCode], but uses [PhoneNumber.preferredDomesticCarrierCode]
     * when present and [fallbackCarrierCode] otherwise. Mirrors
     * `formatNationalNumberWithPreferredCarrierCode`.
     */
    public fun formatNationalNumberWithPreferredCarrierCode(number: PhoneNumber, fallbackCarrierCode: String): String =
        formatNationalNumberWithCarrierCode(number, number.preferredDomesticCarrierCode ?: fallbackCarrierCode)

    /**
     * Format [number] as dialled from [regionCallingFrom], mirroring `formatOutOfCountryCallingNumber`:
     * uses that region's IDD prefix (or its preferred one), drops the country code when the regions
     * share one, and special-cases NANPA. Falls back to INTERNATIONAL when the calling region is
     * unknown.
     */
    public fun formatOutOfCountryCallingNumber(number: PhoneNumber, regionCallingFrom: String): String {
        if (!isValidRegionCode(regionCallingFrom)) return format(number, PhoneNumberFormat.INTERNATIONAL)
        val cc = number.countryCode
        val nsn = getNationalSignificantNumber(number)
        if (cc !in COUNTRY_CODE_TO_MAIN_REGION) return nsn
        val fromMeta = GENERATED_METADATA[regionCallingFrom]!!
        if (cc == NANPA_COUNTRY_CODE) {
            if (isNanpaCountry(regionCallingFrom)) {
                return "$cc " + format(number, PhoneNumberFormat.NATIONAL)
            }
        } else if (cc == fromMeta.countryCode) {
            // Regions sharing a calling code dial nationally.
            return format(number, PhoneNumberFormat.NATIONAL)
        }
        val internationalPrefix = fromMeta.internationalPrefix ?: ""
        val intlPrefixForFormatting = when {
            fromMeta.preferredInternationalPrefix != null -> fromMeta.preferredInternationalPrefix
            compiled(SINGLE_INTERNATIONAL_PREFIX).matches(internationalPrefix) -> internationalPrefix
            else -> ""
        }
        val meta = metadataForCountryCode(cc) ?: return nsn
        val formattedNsn = formatNsn(nsn, meta, PhoneNumberFormat.INTERNATIONAL, null)
        val withExt = maybeAppendFormattedExtension(number, meta, PhoneNumberFormat.INTERNATIONAL, formattedNsn)
        return if (intlPrefixForFormatting.isNotEmpty()) {
            "$intlPrefixForFormatting $cc $withExt"
        } else {
            prefixNumberWithCountryCallingCode(cc, PhoneNumberFormat.INTERNATIONAL, withExt)
        }
    }

    /**
     * Format [number] using a caller-supplied set of [userDefinedFormats], mirroring `formatByPattern`.
     * `$NP`/`$FG` in a user rule's national-prefix rule are expanded against the region's national
     * prefix at call time (user rules are not pre-expanded like the embedded ones).
     */
    public fun formatByPattern(
        number: PhoneNumber,
        numberFormat: PhoneNumberFormat,
        userDefinedFormats: List<NumberFormat>,
    ): String {
        val cc = number.countryCode
        val nsn = getNationalSignificantNumber(number)
        if (cc !in COUNTRY_CODE_TO_MAIN_REGION) return nsn
        val meta = metadataForCountryCode(cc) ?: return nsn
        val chosen = chooseFormattingPatternForNumber(userDefinedFormats, nsn)
        val formattedNsn = if (chosen == null) {
            nsn
        } else {
            val rule = chosen.nationalPrefixFormattingRule
            val effective = if (!rule.isNullOrEmpty()) {
                val np = meta.nationalPrefix
                if (!np.isNullOrEmpty()) {
                    chosen.copy(nationalPrefixFormattingRule = rule.replaceFirst("\$NP", np).replaceFirst("\$FG", "\$1"))
                } else {
                    chosen.copy(nationalPrefixFormattingRule = null)
                }
            } else {
                chosen
            }
            formatNsnUsingPattern(nsn, effective, numberFormat, null)
        }
        val withExt = maybeAppendFormattedExtension(number, meta, numberFormat, formattedNsn)
        return prefixNumberWithCountryCallingCode(cc, numberFormat, withExt)
    }

    /**
     * Format [number] so it can be dialled from a mobile phone in [regionCallingFrom], mirroring
     * `formatNumberForMobileDialing`. Extensions are dropped (they can't be dialled with the number).
     * Region-specific rules (Brazil carrier codes; Mexico/Chile/Uzbekistan and non-geo numbers dialled
     * internationally; NANPA short-number handling) match upstream. When [withFormatting] is false the
     * result is reduced to diallable characters only.
     */
    public fun formatNumberForMobileDialing(number: PhoneNumber, regionCallingFrom: String, withFormatting: Boolean): String {
        val cc = number.countryCode
        if (cc !in COUNTRY_CODE_TO_MAIN_REGION) return number.rawInput ?: ""

        var formattedNumber = ""
        // The extension can't normally be dialled together with the main number.
        val numberNoExt = if (number.extension != null) number.copy(extension = null) else number
        val regionCode = getRegionCodeForCountryCode(cc)
        val numberType = getNumberType(numberNoExt)
        val isValidNumber = numberType != PhoneNumberType.UNKNOWN
        if (regionCallingFrom == regionCode) {
            val isFixedLineOrMobile = numberType == PhoneNumberType.FIXED_LINE ||
                numberType == PhoneNumberType.MOBILE || numberType == PhoneNumberType.FIXED_LINE_OR_MOBILE
            if (regionCode == "BR" && isFixedLineOrMobile) {
                // Brazilian fixed-line/mobile numbers need a carrier code to dial nationally; with none
                // known we return "" (an empty preferred carrier code is treated as absent).
                formattedNumber = if ((numberNoExt.preferredDomesticCarrierCode?.length ?: 0) > 0) {
                    formatNationalNumberWithPreferredCarrierCode(numberNoExt, "")
                } else {
                    ""
                }
            } else if (cc == NANPA_COUNTRY_CODE) {
                // NANPA: international format when internationally diallable and not a short number.
                val regionMetadata = getMetadataForRegion(regionCallingFrom)
                formattedNumber = if (regionMetadata != null && canBeInternationallyDialled(numberNoExt) &&
                    testNumberLength(getNationalSignificantNumber(numberNoExt), regionMetadata, PhoneNumberType.UNKNOWN) != ValidationResult.TOO_SHORT
                ) {
                    format(numberNoExt, PhoneNumberFormat.INTERNATIONAL)
                } else {
                    format(numberNoExt, PhoneNumberFormat.NATIONAL)
                }
            } else {
                // Non-geo entities and MX/CL/UZ fixed-line/mobile numbers dial best in international form.
                formattedNumber = if ((
                        regionCode == REGION_CODE_FOR_NON_GEO_ENTITY ||
                            ((regionCode == "MX" || regionCode == "CL" || regionCode == "UZ") && isFixedLineOrMobile)
                        ) && canBeInternationallyDialled(numberNoExt)
                ) {
                    format(numberNoExt, PhoneNumberFormat.INTERNATIONAL)
                } else {
                    format(numberNoExt, PhoneNumberFormat.NATIONAL)
                }
            }
        } else if (isValidNumber && canBeInternationallyDialled(numberNoExt)) {
            // Short numbers are assumed not diallable from outside their region.
            return if (withFormatting) format(numberNoExt, PhoneNumberFormat.INTERNATIONAL)
            else format(numberNoExt, PhoneNumberFormat.E164)
        }
        return if (withFormatting) formattedNumber else normalizeDiallableCharsOnly(formattedNumber)
    }

    /**
     * Format [number] the way it was originally dialled, mirroring `formatInOriginalFormat`. Requires
     * a number parsed with [parseAndKeepRawInput]: the raw input and [PhoneNumber.countryCodeSource]
     * drive the choice, and the raw input is returned verbatim when reformatting would change the
     * dialled digits. [regionCallingFrom] is used only for the IDD-sourced case.
     */
    public fun formatInOriginalFormat(number: PhoneNumber, regionCallingFrom: String): String {
        val rawInput = number.rawInput
        if (rawInput != null && !hasFormattingPatternForNumber(number)) {
            // Without a formatting pattern we would format as a group with no national prefix.
            return rawInput
        }
        if (number.countryCodeSource == CountryCodeSource.UNSPECIFIED) {
            return format(number, PhoneNumberFormat.NATIONAL)
        }
        var formattedNumber = when (number.countryCodeSource) {
            CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN -> format(number, PhoneNumberFormat.INTERNATIONAL)
            CountryCodeSource.FROM_NUMBER_WITH_IDD -> formatOutOfCountryCallingNumber(number, regionCallingFrom)
            CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN -> format(number, PhoneNumberFormat.INTERNATIONAL).substring(1)
            else -> formatInOriginalFromDefaultCountry(number)
        }
        val raw = number.rawInput
        if (raw != null && raw.isNotEmpty() &&
            normalizeDiallableCharsOnly(formattedNumber) != normalizeDiallableCharsOnly(raw)
        ) {
            // Reformatting changed a dialled digit; return exactly what the user entered.
            formattedNumber = raw
        }
        return formattedNumber
    }

    /** The FROM_DEFAULT_COUNTRY branch of [formatInOriginalFormat]: decide whether to keep the national prefix. */
    private fun formatInOriginalFromDefaultCountry(number: PhoneNumber): String {
        val regionCode = getRegionCodeForCountryCode(number.countryCode)
        val nationalPrefix = getNddPrefixForRegion(regionCode, stripNonDigits = true)
        val nationalFormat = format(number, PhoneNumberFormat.NATIONAL)
        if (nationalPrefix.isNullOrEmpty()) return nationalFormat
        if (rawInputContainsNationalPrefix(number.rawInput ?: "", nationalPrefix, regionCode)) return nationalFormat
        val meta = getMetadataForRegion(regionCode) ?: return nationalFormat
        val nsn = getNationalSignificantNumber(number)
        val formatRule = chooseFormattingPatternForNumber(meta.numberFormats, nsn) ?: return nationalFormat
        val candidate = formatRule.nationalPrefixFormattingRule ?: ""
        val indexOfFirstGroup = candidate.indexOf("\$1")
        if (indexOfFirstGroup <= 0) return nationalFormat
        val prefixPart = normalizeDigitsOnly(candidate.substring(0, indexOfFirstGroup))
        if (prefixPart.isEmpty()) return nationalFormat
        // The national prefix wasn't in the input, so format without it.
        val noPrefixRule = formatRule.copy(nationalPrefixFormattingRule = null)
        return formatByPattern(number, PhoneNumberFormat.NATIONAL, listOf(noPrefixRule))
    }

    /**
     * Format [number] as dialled from [regionCallingFrom] while keeping any alpha characters from the
     * original raw input, mirroring `formatOutOfCountryKeepingAlphaChars`. Requires a number parsed
     * with [parseAndKeepRawInput]; with no raw input it falls back to [formatOutOfCountryCallingNumber].
     */
    public fun formatOutOfCountryKeepingAlphaChars(number: PhoneNumber, regionCallingFrom: String): String {
        var rawInput = number.rawInput ?: ""
        if (rawInput.isEmpty()) return formatOutOfCountryCallingNumber(number, regionCallingFrom)
        val cc = number.countryCode
        if (cc !in COUNTRY_CODE_TO_MAIN_REGION) return rawInput
        // Retain only number-grouping symbols and alpha characters (upstream keeps display grouping).
        rawInput = normalizeKeepingGrouping(rawInput)
        // Trim everything before the first three digits of the national number (all valid alpha
        // numbers start with three digits); if the NSN is shorter, or not found, trim nothing.
        val nsn = getNationalSignificantNumber(number)
        if (nsn.length > 3) {
            val idx = rawInput.indexOf(nsn.substring(0, 3))
            if (idx != -1) rawInput = rawInput.substring(idx)
        }
        val metaFrom = getMetadataForRegion(regionCallingFrom)
        if (cc == NANPA_COUNTRY_CODE) {
            if (isNanpaCountry(regionCallingFrom)) return "$cc $rawInput"
        } else if (metaFrom != null && cc == metaFrom.countryCode) {
            val fmtPattern = chooseFormattingPatternForNumber(metaFrom.numberFormats, nsn) ?: return rawInput
            // Reformat the raw input's grouping into a single group, keeping any national prefix rule.
            val newFormat = fmtPattern.copy(pattern = "(\\d+)(.*)", format = "\$1\$2")
            return formatNsnUsingPattern(rawInput, newFormat, PhoneNumberFormat.NATIONAL, null)
        }
        var intlPrefixForFormatting = ""
        if (metaFrom != null) {
            val internationalPrefix = metaFrom.internationalPrefix ?: ""
            intlPrefixForFormatting = if (compiled(SINGLE_INTERNATIONAL_PREFIX).matches(internationalPrefix)) {
                internationalPrefix
            } else {
                metaFrom.preferredInternationalPrefix ?: ""
            }
        }
        val metaForRegion = getMetadataForRegionOrCallingCode(cc, getRegionCodeForCountryCode(cc))
        var formatted = rawInput
        if (metaForRegion != null) {
            formatted = maybeAppendFormattedExtension(number, metaForRegion, PhoneNumberFormat.INTERNATIONAL, formatted)
        }
        return if (intlPrefixForFormatting.isNotEmpty()) {
            "$intlPrefixForFormatting $cc $formatted"
        } else {
            prefixNumberWithCountryCallingCode(cc, PhoneNumberFormat.INTERNATIONAL, formatted)
        }
    }

    /** Canonical grouping character for [c] (ASCII), or null to drop it — libphonenumber's ALL_PLUS_NUMBER_GROUPING_SYMBOLS. */
    private fun groupingChar(c: Char): Char? = when {
        c in '0'..'9' -> c
        c in 'A'..'Z' -> c
        c in 'a'..'z' -> c.uppercaseChar()
        c == '-' || c == '－' || c in '‐'..'―' || c == '−' -> '-'
        c == '/' || c == '／' -> '/'
        c == ' ' || c == '　' || c == '⁠' -> ' '
        c == '.' || c == '．' -> '.'
        else -> null
    }

    /** Keep digits, alpha (upper-cased) and grouping symbols; drop everything else. */
    private fun normalizeKeepingGrouping(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) groupingChar(c)?.let { sb.append(it) }
        return sb.toString()
    }

    private fun hasFormattingPatternForNumber(number: PhoneNumber): Boolean {
        val cc = number.countryCode
        val meta = getMetadataForRegionOrCallingCode(cc, getRegionCodeForCountryCode(cc)) ?: return false
        return chooseFormattingPatternForNumber(meta.numberFormats, getNationalSignificantNumber(number)) != null
    }

    /** Whether [rawInput] began with [nationalPrefix] and the number stays valid once it is removed. */
    private fun rawInputContainsNationalPrefix(rawInput: String, nationalPrefix: String, regionCode: String): Boolean {
        val normalized = normalizeDigitsOnly(rawInput)
        if (!normalized.startsWith(nationalPrefix)) return false
        return try {
            isValidNumber(parse(normalized.substring(nationalPrefix.length), regionCode))
        } catch (e: NumberParseException) {
            false
        }
    }

    /** The national significant number (this port stores leading zeros in [PhoneNumber.nationalNumber]). */
    public fun getNationalSignificantNumber(number: PhoneNumber): String = number.nationalNumber

    private fun metadataForCountryCode(cc: Int): PhoneMetadata? =
        COUNTRY_CODE_TO_MAIN_REGION[cc]?.let { GENERATED_METADATA[it] }

    /** Port of `formatNsn`: pick a display template for [number] and apply it (or return it as-is). */
    private fun formatNsn(number: String, meta: PhoneMetadata, numberFormat: PhoneNumberFormat, carrierCode: String?): String {
        val intl = meta.intlNumberFormats
        val available = if (intl.isEmpty() || numberFormat == PhoneNumberFormat.NATIONAL) meta.numberFormats else intl
        val pattern = chooseFormattingPatternForNumber(available, number) ?: return number
        return formatNsnUsingPattern(number, pattern, numberFormat, carrierCode)
    }

    /**
     * Port of `chooseFormattingPatternForNumber`: the last (most detailed) leading-digits pattern
     * must match at the start, and the full pattern must match the whole national number.
     */
    private fun chooseFormattingPatternForNumber(available: List<NumberFormat>, nsn: String): NumberFormat? {
        for (nf in available) {
            val n = nf.leadingDigitsPatterns.size
            if (n == 0 || compiled(nf.leadingDigitsPatterns[n - 1]).matchAtStart(nsn) != null) {
                if (compiled(nf.pattern).matches(nsn)) return nf
            }
        }
        return null
    }

    /** Port of `formatNsnUsingPattern`: substitute the capture groups into the chosen template. */
    private fun formatNsnUsingPattern(
        nsn: String,
        nf: NumberFormat,
        numberFormat: PhoneNumberFormat,
        carrierCode: String?,
    ): String {
        val match = compiled(nf.pattern).matchAtStart(nsn) ?: return nsn
        val groups = match.groups
        val carrierRule = nf.domesticCarrierCodeFormattingRule
        val rule: String = when {
            numberFormat == PhoneNumberFormat.NATIONAL && !carrierCode.isNullOrEmpty() && !carrierRule.isNullOrEmpty() -> {
                // Insert the carrier code into its rule, then place that as the first group.
                applyFirstGroupRule(nf.format, carrierRule.replace(CARRIER_CODE_PLACEHOLDER, carrierCode))
            }
            numberFormat == PhoneNumberFormat.NATIONAL && !nf.nationalPrefixFormattingRule.isNullOrEmpty() -> {
                applyFirstGroupRule(nf.format, nf.nationalPrefixFormattingRule)
            }
            else -> nf.format
        }
        var formatted = expandTransform(rule, groups)
        if (numberFormat == PhoneNumberFormat.RFC3966) {
            // Strip any leading separator, then collapse each remaining separator run to a hyphen.
            formatted = stripAndCollapseSeparators(formatted)
        }
        return formatted
    }

    /** Replace the first `$<digit>` token in [format] with [rule] (libphonenumber's FIRST_GROUP_PATTERN). */
    private fun applyFirstGroupRule(format: String, rule: String): String {
        var i = 0
        while (i < format.length) {
            if (format[i] == '$' && i + 1 < format.length && format[i + 1] in '0'..'9') {
                return format.substring(0, i) + rule + format.substring(i + 2)
            }
            i++
        }
        return format
    }

    /** True for the separator characters libphonenumber's SEPARATOR_PATTERN recognises. */
    private fun isFormatSeparator(c: Char): Boolean = when (c) {
        '-', 'x', ' ', '(', ')', '.', '[', ']', '/', '~',
        '‐', '‑', '‒', '–', '—', '―', '−', 'ー',
        '－', '．', '／', ' ', '­', '​', '⁠', '　',
        '（', '）', '［', '］', '⁓', '∼', '～',
        -> true
        else -> false
    }

    /** For RFC3966: drop a leading separator run, then replace each remaining separator run with "-". */
    private fun stripAndCollapseSeparators(s: String): String {
        var i = 0
        while (i < s.length && isFormatSeparator(s[i])) i++
        val sb = StringBuilder(s.length)
        var inSep = false
        while (i < s.length) {
            val c = s[i]
            if (isFormatSeparator(c)) {
                if (!inSep) sb.append('-')
                inSep = true
            } else {
                sb.append(c)
                inSep = false
            }
            i++
        }
        return sb.toString()
    }

    /** Port of `prefixNumberWithCountryCallingCode`: add the "+cc" / "tel:+cc-" lead per format. */
    private fun prefixNumberWithCountryCallingCode(cc: Int, numberFormat: PhoneNumberFormat, formattedNsn: String): String =
        when (numberFormat) {
            PhoneNumberFormat.E164 -> "+$cc$formattedNsn"
            PhoneNumberFormat.INTERNATIONAL -> "+$cc $formattedNsn"
            PhoneNumberFormat.RFC3966 -> "$RFC3966_PREFIX+$cc-$formattedNsn"
            PhoneNumberFormat.NATIONAL -> formattedNsn
        }

    /** Port of `maybeAppendFormattedExtension`: append the extension with the region's preferred prefix. */
    private fun maybeAppendFormattedExtension(
        number: PhoneNumber,
        meta: PhoneMetadata,
        numberFormat: PhoneNumberFormat,
        formatted: String,
    ): String {
        val ext = number.extension
        if (ext.isNullOrEmpty()) return formatted
        return if (numberFormat == PhoneNumberFormat.RFC3966) {
            formatted + RFC3966_EXTN_PREFIX + ext
        } else {
            formatted + (meta.preferredExtnPrefix ?: DEFAULT_EXTN_PREFIX) + ext
        }
    }

    /**
     * True if the first meaningful character of [input] is a "+" (ASCII U+002B or
     * fullwidth U+FF0B), i.e. the number is in international form. Leading
     * characters that are neither a plus nor a decimal digit are skipped; the first
     * decimal digit with no preceding plus means national form. Iterates by code
     * point and uses the frozen digit table, so the result never depends on the
     * platform's Char.isWhitespace() table.
     */
    private fun leadsWithPlus(input: CharSequence): Boolean {
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '+' || c == '＋') return true // ASCII or fullwidth plus
            val cp: Int
            if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (input[i + 1].code - 0xDC00)
                i += 2
            } else {
                cp = c.code
                i += 1
            }
            if (decimalDigitValue(cp) != null) return false
        }
        return false
    }

    // Compiled-pattern cache. Behaviour is identical to constructing a PhonePattern
    // per call; the cache only avoids re-parsing hot patterns.
    private val patternCache = HashMap<String, PhonePattern>()
    private fun compiled(pattern: String): PhonePattern =
        patternCache.getOrPut(pattern) { PhonePattern(pattern) }

    private data class StripResult(val number: String, val source: CountryCodeSource)
    private data class CcExtraction(val countryCode: Int, val nationalNumber: String, val source: CountryCodeSource)

    /**
     * Port of libphonenumber's `maybeExtractCountryCode`. [digits] is the digit-normalized number,
     * [hasPlus] whether the input led with "+", [meta] the default region's metadata (may be null).
     * Strips any international prefix / IDD, then reads a country calling code from what remains.
     * Returns the code (0 if none was found), the remaining national number, and how the code was
     * sourced. Throws TOO_SHORT_AFTER_IDD when an IDD (or "+") is followed by too few digits, and
     * INVALID_COUNTRY_CODE when the leading digits are not a recognised calling code.
     */
    private fun maybeExtractCountryCode(digits: String, hasPlus: Boolean, meta: PhoneMetadata?): CcExtraction {
        if (digits.isEmpty()) return CcExtraction(0, digits, CountryCodeSource.FROM_DEFAULT_COUNTRY)
        val strip = maybeStripInternationalPrefix(digits, hasPlus, meta?.internationalPrefix)
        val fullNumber = strip.number
        if (strip.source != CountryCodeSource.FROM_DEFAULT_COUNTRY) {
            if (fullNumber.length <= MIN_LENGTH_FOR_NSN) {
                throw NumberParseException(
                    ErrorType.TOO_SHORT_AFTER_IDD,
                    "Phone number had an IDD, but after this was not long enough to be a viable phone number",
                )
            }
            val extracted = extractCountryCode(fullNumber)
            if (extracted != null) return CcExtraction(extracted.first, extracted.second, strip.source)
            throw NumberParseException(ErrorType.INVALID_COUNTRY_CODE, "Country calling code supplied was not recognised")
        } else if (meta != null) {
            // The number may begin with the default region's own calling code. Strip it only if that
            // gives a better result — the number was invalid but becomes valid, or was too long —
            // matching libphonenumber's keep-better-result comparison. This is what parses the
            // wild "64(0)64123456" (NZ) and "1-650-253-0000" (US) national forms.
            val defaultCc = meta.countryCode
            val defaultCcStr = defaultCc.toString()
            if (fullNumber.startsWith(defaultCcStr)) {
                val potential = maybeStripNationalPrefix(fullNumber.substring(defaultCcStr.length), meta)
                val general = compiled(meta.generalNationalNumberPattern)
                val keepStripped = (!general.matches(fullNumber) && general.matches(potential)) ||
                    testNumberLength(fullNumber, meta, PhoneNumberType.UNKNOWN) == ValidationResult.TOO_LONG
                if (keepStripped) {
                    return CcExtraction(defaultCc, potential, CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN)
                }
            }
        }
        return CcExtraction(0, digits, CountryCodeSource.FROM_DEFAULT_COUNTRY)
    }

    /**
     * Port of `maybeStripInternationalPrefixAndNormalize`. [digits] is already digit-normalized with
     * any "+" removed; [hasPlus] whether the input led with "+". Returns the number with an
     * international prefix removed (if one applied) and the country-code source.
     */
    private fun maybeStripInternationalPrefix(digits: String, hasPlus: Boolean, iddPattern: String?): StripResult {
        if (digits.isEmpty()) return StripResult(digits, CountryCodeSource.FROM_DEFAULT_COUNTRY)
        if (hasPlus) return StripResult(digits, CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN)
        if (iddPattern != null) {
            parsePrefixAsIdd(iddPattern, digits)?.let { return StripResult(it, CountryCodeSource.FROM_NUMBER_WITH_IDD) }
        }
        return StripResult(digits, CountryCodeSource.FROM_DEFAULT_COUNTRY)
    }

    /**
     * Port of `parsePrefixAsIdd`: if [number] begins with the region's IDD pattern, strip it — unless
     * the first digit after the match is 0 (country calling codes never begin with 0). Returns the
     * stripped number, or null when the prefix does not apply.
     */
    private fun parsePrefixAsIdd(iddPattern: String, number: String): String? {
        val m = compiled(iddPattern).matchAtStart(number) ?: return null
        val rest = number.substring(m.end)
        // [number] is already digit-normalized (ASCII), so the first char is the first decimal digit.
        if (rest.firstOrNull { it in '0'..'9' } == '0') return null
        return rest
    }

    /**
     * Port of `extractCountryCode`: read a 1–3 digit country calling code from the front of
     * [fullNumber]. Returns the code and the remaining national number, or null when none is found
     * (or the number begins with 0, which no calling code does).
     */
    private fun extractCountryCode(fullNumber: String): Pair<Int, String>? {
        if (fullNumber.isEmpty() || fullNumber[0] == '0') return null
        val numberLength = fullNumber.length
        var i = 1
        while (i <= MAX_LENGTH_COUNTRY_CODE && i <= numberLength) {
            val potential = fullNumber.substring(0, i).toInt()
            if (potential in COUNTRY_CODE_TO_MAIN_REGION) return potential to fullNumber.substring(i)
            i++
        }
        return null
    }

    // --- RFC3966 tel: URI / phone-context handling (#8) ---

    private const val RFC3966_PREFIX = "tel:"
    private const val RFC3966_PHONE_CONTEXT = ";phone-context="
    private const val RFC3966_ISDN_SUBADDRESS = ";isub="

    /**
     * Build the number to parse from [numberToParse], resolving an RFC3966 `tel:` URI and its
     * `;phone-context=` (a `+`-prefixed global number is prepended; a domain context is ignored),
     * dropping `;isub=` and, for a non-RFC3966 input, trimming leading junk. Ports
     * `buildNationalNumberForParsing`. Throws NOT_A_NUMBER for an invalid phone-context.
     */
    private fun buildNationalNumberForParsing(numberToParse: String): String {
        val idx = numberToParse.indexOf(RFC3966_PHONE_CONTEXT)
        val phoneContext = extractPhoneContext(numberToParse, idx)
        if (!isPhoneContextValid(phoneContext)) {
            throw NumberParseException(ErrorType.NOT_A_NUMBER, "The phone-context value is invalid")
        }
        var national = if (phoneContext != null) {
            val prefixPart = if (phoneContext.startsWith("+")) phoneContext else ""
            val telIdx = numberToParse.indexOf(RFC3966_PREFIX)
            val nnStart = if (telIdx >= 0) telIdx + RFC3966_PREFIX.length else 0
            prefixPart + numberToParse.substring(nnStart, idx)
        } else {
            extractPossibleNumber(numberToParse)
        }
        val isub = national.indexOf(RFC3966_ISDN_SUBADDRESS)
        if (isub > 0) national = national.substring(0, isub)
        return national
    }

    /** Value of the `;phone-context=` parameter, "" if empty, or null if absent. */
    private fun extractPhoneContext(numberToExtractFrom: String, indexOfPhoneContext: Int): String? {
        if (indexOfPhoneContext == -1) return null
        val start = indexOfPhoneContext + RFC3966_PHONE_CONTEXT.length
        if (start >= numberToExtractFrom.length) return ""
        val end = numberToExtractFrom.indexOf(';', start)
        return if (end != -1) numberToExtractFrom.substring(start, end) else numberToExtractFrom.substring(start)
    }

    /** Whether a phone-context follows RFC3966: null is fine, "" is not, else global-digits or domain. */
    private fun isPhoneContextValid(phoneContext: String?): Boolean {
        if (phoneContext == null) return true
        if (phoneContext.isEmpty()) return false
        return isRfc3966GlobalNumberDigits(phoneContext) || isRfc3966DomainName(phoneContext)
    }

    /** `^\+ (digit | [-.()])* digit (digit | [-.()])* $` — a "+"-led number with at least one digit. */
    private fun isRfc3966GlobalNumberDigits(s: String): Boolean {
        if (s.isEmpty() || s[0] != '+') return false
        var hasDigit = false
        for (i in 1 until s.length) {
            val c = s[i]
            when {
                c in '0'..'9' -> hasDigit = true
                c == '-' || c == '.' || c == '(' || c == ')' -> {}
                else -> return false
            }
        }
        return hasDigit
    }

    /** RFC3966 domainname: dot-separated labels (alnum, interior hyphens), the last starting with a letter. */
    private fun isRfc3966DomainName(context: String): Boolean {
        val s = if (context.endsWith(".")) context.dropLast(1) else context
        if (s.isEmpty()) return false
        val labels = s.split('.')
        for ((i, label) in labels.withIndex()) {
            if (label.isEmpty()) return false
            if (label.first() == '-' || label.last() == '-') return false
            if (!label.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' }) return false
            // The top (last) label must start with a letter.
            if (i == labels.lastIndex && label.first() !in 'A'..'Z' && label.first() !in 'a'..'z') return false
        }
        return true
    }

    /**
     * Return [input] from its first "+" (ASCII/fullwidth) or Unicode decimal digit onward, dropping
     * any leading junk — a reduced port of libphonenumber's `extractPossibleNumber` (trailing-junk
     * trimming and second-number cutting are the documented #3 divergence and are not done). Empty
     * if there is no such character.
     */
    private fun extractPossibleNumber(input: String): String {
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '+' || c == '＋') return input.substring(i)
            val cp: Int
            val next: Int
            if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (input[i + 1].code - 0xDC00)
                next = i + 2
            } else {
                cp = c.code
                next = i + 1
            }
            if (decimalDigitValue(cp) != null) return input.substring(i)
            i = next
        }
        return ""
    }

    // --- Alpha (vanity) numbers (#9) ---

    /** True if [input] contains at least three ASCII letters (libphonenumber's alpha-number rule). */
    private fun hasAtLeastThreeAlpha(input: String): Boolean {
        var n = 0
        for (c in input) {
            if (c in 'A'..'Z' || c in 'a'..'z') {
                if (++n == 3) return true
            }
        }
        return false
    }

    /** Phone-keypad digit for an ASCII letter, mirroring libphonenumber's ALPHA_MAPPINGS. */
    private fun keypadDigit(c: Char): Char? = when (c.uppercaseChar()) {
        'A', 'B', 'C' -> '2'
        'D', 'E', 'F' -> '3'
        'G', 'H', 'I' -> '4'
        'J', 'K', 'L' -> '5'
        'M', 'N', 'O' -> '6'
        'P', 'Q', 'R', 'S' -> '7'
        'T', 'U', 'V' -> '8'
        'W', 'X', 'Y', 'Z' -> '9'
        else -> null
    }

    /**
     * Normalize an alpha (vanity) number for parsing: each letter becomes its keypad digit, each
     * Unicode decimal digit becomes ASCII, everything else is dropped. This is `normalize` applied to
     * an alpha number (`normalizeHelper` with remove-non-matches). The public
     * [convertAlphaCharactersInNumber] differs: it keeps punctuation.
     */
    private fun alphaConvertForParsing(input: String, libphonenumberCompat: Boolean): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val cp: Int
            if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (input[i + 1].code - 0xDC00)
                i += 2
            } else {
                cp = c.code
                i += 1
            }
            val letter = if (cp <= 0xFFFF) keypadDigit(cp.toChar()) else null
            if (letter != null) {
                sb.append(letter)
            } else if (!(libphonenumberCompat && cp > 0xFFFF)) {
                decimalDigitValue(cp)?.let { sb.append('0' + it) }
            }
        }
        return sb.toString()
    }

    // --- Extension stripping (#5), a hand-written port of libphonenumber's EXTN_PATTERN /
    // maybeStripExtension. Deterministic on every target (no regex engine). ---

    private fun isExtSep(c: Char): Boolean = c == ' ' || c == ' ' || c == '\t' || c == ','

    /** ASCII value of an ASCII or fullwidth decimal digit, else null. */
    private fun extDigit(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '０'..'９' -> c - '０'
        else -> null
    }

    // Explicit labels capture up to 20 extension digits; ambiguous single-char labels up to 9.
    // Lower-cased for case-insensitive matching. Longer forms first so "extn" wins over "ext".
    private val EXPLICIT_EXT_LABELS =
        listOf("extension", "extensión", "exten", "extn", "ext", "xtn", "xt", "anexo", "доб", "ｅｘｔｎ", "ｘｔｎ", "ｘｔ")
    private val AMBIGUOUS_EXT_LABELS =
        listOf("int", "ｉｎｔ", "x", "ｘ", "#", "＃", "~", "～")

    /**
     * Split a recognised phone extension off [input], returning (main, extension) with the
     * extension digits in ASCII, or (input, null) if none. Ports maybeStripExtension: RFC3966
     * ";ext=", explicit labels (ext/extn/extension/xt/xtn/доб/anexo/full-width), ambiguous single
     * chars (x/#/~/int), auto-dialling (",,"/";"), and the American trailing-"#" form. The match
     * must run to the end of the string (only separators may trail), so a carrier code such as the
     * "xx" in "011xx5481429712" is never taken for an extension. The part before the extension must
     * itself look like a phone number.
     */
    private fun stripExtension(input: String): Pair<String, String?> {
        val lower = input.lowercase()
        var i = 0
        while (i < input.length) {
            val ext = extensionAt(input, lower, i)
            if (ext != null && isViablePhoneNumber(input.substring(0, i))) {
                return input.substring(0, i) to ext
            }
            i++
        }
        return input to null
    }

    private fun extensionAt(s: String, lower: String, start: Int): String? {
        if (lower.startsWith(";ext=", start)) return readExtTail(s, start + 5, 20)
        if (s.startsWith(",,", start)) return readExtTail(s, start + 2, 15)
        if (s[start] == ';') return readExtTail(s, start + 1, 15)
        var j = start
        while (j < s.length && isExtSep(s[j])) j++
        if (j >= s.length) return null
        for (label in EXPLICIT_EXT_LABELS) {
            if (lower.startsWith(label, j)) return readExtTail(s, j + label.length, 20)
        }
        for (label in AMBIGUOUS_EXT_LABELS) {
            if (lower.startsWith(label, j)) return readExtTail(s, j + label.length, 9)
        }
        // American style: separators then digits then a required '#'.
        if (j > start) return readExtTail(s, j, 6, requireHash = true)
        return null
    }

    /**
     * Read the digits of an extension: an optional `[:.]`, optional separators/hyphens, then 1..[limit]
     * digits, then an optional (or required, for the American form) `#`. The remainder must be
     * separators only — the extension is a suffix. Returns the ASCII digits, or null.
     */
    private fun readExtTail(s: String, from: Int, limit: Int, requireHash: Boolean = false): String? {
        var k = from
        if (k < s.length && (s[k] == ':' || s[k] == '.' || s[k] == '．')) k++
        while (k < s.length && (isExtSep(s[k]) || s[k] == '-')) k++
        val sb = StringBuilder()
        while (k < s.length && sb.length < limit) {
            val d = extDigit(s[k]) ?: break
            sb.append('0' + d)
            k++
        }
        if (sb.isEmpty()) return null
        var hashed = false
        if (k < s.length && (s[k] == '#' || s[k] == '＃')) {
            k++
            hashed = true
        }
        if (requireHash && !hashed) return null
        while (k < s.length && isExtSep(s[k])) k++
        return if (k == s.length) sb.toString() else null
    }

    /** Result of stripping the national prefix: the national number and any captured carrier code. */
    private data class PrefixStrip(val number: String, val carrierCode: String)

    /** Strip the national (trunk) prefix, discarding any carrier code (see [maybeStripNationalPrefixAndCarrier]). */
    private fun maybeStripNationalPrefix(number: String, meta: PhoneMetadata): String =
        maybeStripNationalPrefixAndCarrier(number, meta).number

    /**
     * Strip the national (trunk) prefix and apply any transform rule, faithfully porting
     * libphonenumber's `maybeStripNationalPrefixAndCarrierCode`. Returns the national significant
     * number (unchanged if no prefix applies, or if stripping/transforming would make a
     * previously-viable number non-viable) together with any captured carrier-selection code ("" if
     * none) — the carrier code is only retained for `parseAndKeepRawInput`.
     */
    private fun maybeStripNationalPrefixAndCarrier(number: String, meta: PhoneMetadata): PrefixStrip {
        // libphonenumber's metadata build defaults nationalPrefixForParsing to the national prefix
        // when absent; we replicate that fallback. Over-stripping (e.g. US "1" off "1234567890")
        // is undone by the possible-length guard in parse(), not by suppressing the strip here.
        val possibleNationalPrefix = meta.nationalPrefixForParsing ?: meta.nationalPrefix
        if (number.isEmpty() || possibleNationalPrefix.isNullOrEmpty()) return PrefixStrip(number, "")

        val prefixMatch = compiled(possibleNationalPrefix).matchAtStart(number) ?: return PrefixStrip(number, "")
        val general = compiled(meta.generalNationalNumberPattern)
        val isViableOriginal = general.matches(number)

        val groups = prefixMatch.groups
        val numOfGroups = groups.size - 1
        val lastGroup = if (numOfGroups >= 1) groups[numOfGroups] else null
        val transformRule = meta.nationalPrefixTransformRule
        val firstGroup = groups.getOrNull(1) ?: ""

        return if (transformRule.isNullOrEmpty() || lastGroup == null) {
            // No transform: strip the matched prefix. Revert if that makes a previously-viable
            // number non-viable. Carrier code is the first group when a (last) group was captured.
            val stripped = number.substring(prefixMatch.end)
            if (isViableOriginal && !general.matches(stripped)) {
                PrefixStrip(number, "")
            } else {
                PrefixStrip(stripped, if (numOfGroups > 0 && lastGroup != null) firstGroup else "")
            }
        } else {
            // Transform: replace the matched prefix with the expanded rule, keep the remainder.
            // Revert if the result isn't viable. Carrier code is the first group.
            val transformed = expandTransform(transformRule, groups) + number.substring(prefixMatch.end)
            if (isViableOriginal && !general.matches(transformed)) {
                PrefixStrip(number, "")
            } else {
                PrefixStrip(transformed, if (numOfGroups > 1) firstGroup else "")
            }
        }
    }

    /** Expand a national-prefix transform rule, substituting `$n` with capture group n. */
    private fun expandTransform(rule: String, groups: List<String?>): String {
        val sb = StringBuilder(rule.length)
        var i = 0
        while (i < rule.length) {
            val c = rule[i]
            if (c == '$' && i + 1 < rule.length && rule[i + 1] in '0'..'9') {
                val g = rule[i + 1] - '0'
                sb.append(groups.getOrNull(g) ?: "")
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
