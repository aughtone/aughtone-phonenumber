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
 * Multiplatform. Behaviour follows the original for the E.164 path; this is a
 * reduced subset (parse to national significant number, format to E.164, and a
 * general-pattern validity check) that the byte-stable normalizer needs.
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
 */
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
 * Minimal, byte-stable phone-number parser and E.164 formatter driven by the
 * embedded [GENERATED_METADATA]. Not yet the full libphonenumber algorithm — it
 * covers the E.164 normalization path (digit extraction, country-code handling,
 * IDD stripping, guarded national-prefix stripping) and a general-pattern
 * validity check. Full per-type validation, possible-length checks, alpha
 * numbers, and Unicode digit normalization are follow-ups.
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
        val base = parseMain(main, defaultRegion, libphonenumberCompat)
        return if (extension != null) base.copy(extension = extension) else base
    }

    /** Parse the number proper (extension already removed), mirroring libphonenumber's parseHelper. */
    private fun parseMain(number: String, defaultRegion: String, libphonenumberCompat: Boolean): PhoneNumber {
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
            convertAlphaCharactersInNumber(possible, libphonenumberCompat)
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

        // Strip the national prefix, but keep the stripped result only if it is still a possible
        // length for the region — otherwise the original was likely a valid short number
        // (libphonenumber's parseHelper does exactly this after maybeStripNationalPrefixAndCarrierCode).
        if (meta != null) {
            val stripped = maybeStripNationalPrefix(nationalNumber, meta)
            when (testNumberLength(stripped, meta, PhoneNumberType.UNKNOWN)) {
                ValidationResult.TOO_SHORT, ValidationResult.IS_POSSIBLE_LOCAL_ONLY, ValidationResult.INVALID_LENGTH -> {}
                else -> nationalNumber = stripped
            }
        }
        return validateLength(PhoneNumber(countryCode, nationalNumber))
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
     * True if [nsn] is a valid number for [meta]'s region — i.e. libphonenumber's
     * getNumberType would return a known type: the number matches the general
     * pattern and at least one specific type pattern.
     */
    private fun isValidForRegion(nsn: String, meta: PhoneMetadata): Boolean {
        if (!compiled(meta.generalNationalNumberPattern).matches(nsn)) return false
        return meta.typePatterns.values.any { compiled(it).matches(nsn) }
    }

    /** Number types, mirroring libphonenumber's `PhoneNumberType`. */
    public enum class PhoneNumberType {
        FIXED_LINE, MOBILE, FIXED_LINE_OR_MOBILE, TOLL_FREE, PREMIUM_RATE,
        SHARED_COST, VOIP, PERSONAL_NUMBER, PAGER, UAN, VOICEMAIL, UNKNOWN,
    }

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

    /** True if [number] is valid — general + a specific type pattern match (see [isValid]). */
    private fun isValidNumber(number: PhoneNumber): Boolean {
        val region = getRegionForNumber(number.countryCode, number.nationalNumber) ?: return false
        val meta = GENERATED_METADATA[region] ?: return false
        return isValidForRegion(number.nationalNumber, meta)
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
     * Convert an alpha (vanity) number to digits: each letter becomes its keypad digit, each Unicode
     * decimal digit becomes ASCII, everything else is dropped. Ports `convertAlphaCharactersInNumber`.
     */
    private fun convertAlphaCharactersInNumber(input: String, libphonenumberCompat: Boolean): String {
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

    /**
     * Strip the national (trunk) prefix and apply any transform rule, faithfully
     * porting libphonenumber's `maybeStripNationalPrefixAndCarrierCode`. Returns
     * the national significant number (unchanged if no prefix applies, or if
     * stripping/transforming would make a previously-viable number non-viable).
     * Carrier-code capture is not retained (not needed for E.164).
     */
    private fun maybeStripNationalPrefix(number: String, meta: PhoneMetadata): String {
        // libphonenumber's metadata build defaults nationalPrefixForParsing to the national prefix
        // when absent; we replicate that fallback. Over-stripping (e.g. US "1" off "1234567890")
        // is undone by the possible-length guard in parse(), not by suppressing the strip here.
        val possibleNationalPrefix = meta.nationalPrefixForParsing ?: meta.nationalPrefix
        if (number.isEmpty() || possibleNationalPrefix.isNullOrEmpty()) return number

        val prefixMatch = compiled(possibleNationalPrefix).matchAtStart(number) ?: return number
        val general = compiled(meta.generalNationalNumberPattern)
        val isViableOriginal = general.matches(number)

        val groups = prefixMatch.groups
        val numOfGroups = groups.size - 1
        val lastGroup = if (numOfGroups >= 1) groups[numOfGroups] else null
        val transformRule = meta.nationalPrefixTransformRule

        return if (transformRule.isNullOrEmpty() || lastGroup == null) {
            // No transform: strip the matched prefix. Revert if that makes a
            // previously-viable number non-viable.
            val stripped = number.substring(prefixMatch.end)
            if (isViableOriginal && !general.matches(stripped)) number else stripped
        } else {
            // Transform: replace the matched prefix with the expanded rule, keep
            // the remainder. Revert if the result isn't viable.
            val transformed = expandTransform(transformRule, groups) + number.substring(prefixMatch.end)
            if (isViableOriginal && !general.matches(transformed)) number else transformed
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
