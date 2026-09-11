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

/**
 * A parsed phone number reduced to what canonical E.164 needs: the country
 * calling code and the national significant number.
 */
public data class PhoneNumber internal constructor(
    val countryCode: Int,
    val nationalNumber: String,
) {
    /** Canonical E.164, e.g. "+16502530000". Byte-stable within a metadata epoch. */
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

    /** The embedded libphonenumber metadata epoch (see [METADATA_VERSION]). */
    public val metadataVersion: String get() = METADATA_VERSION

    /** The Unicode version of the embedded decimal-digit table (see [DIGIT_UNICODE_VERSION]). */
    public val digitUnicodeVersion: String get() = DIGIT_UNICODE_VERSION

    /** Why a parse failed. Mirrors the upstream error categories this subset raises. */
    public enum class ErrorType { NOT_A_NUMBER, INVALID_COUNTRY_CODE }

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
        val meta = GENERATED_METADATA[defaultRegion]
            ?: throw NumberParseException(ErrorType.NOT_A_NUMBER, "Unknown default region")

        // Detect a leading international "+" deterministically: scan code points for
        // the first ASCII/fullwidth plus or the first Unicode decimal digit,
        // ignoring anything before it. This is independent of the platform's
        // Char.isWhitespace() table, so leading spacing/formatting cannot change
        // the outcome across targets.
        val hasPlus = leadsWithPlus(number)
        // Normalize every Unicode decimal digit to its ASCII value; drops all other
        // characters. Deterministic on every target (frozen table), not reliant on
        // platform Char.digitToInt / \p{Nd}.
        val digits = normalizeDigitsOnly(number, libphonenumberCompat)
        if (digits.isEmpty()) throw NumberParseException(ErrorType.NOT_A_NUMBER, "No digits in input")

        if (hasPlus) return fromInternational(digits, defaultRegion)

        // National form may still be an IDD-dialed international number.
        val idd = meta.internationalPrefix
        if (idd != null) {
            val m = compiled(idd).matchAtStart(digits)
            if (m != null && m.end < digits.length) {
                val intl = tryFromInternational(digits.substring(m.end), defaultRegion)
                if (intl != null) return intl
            }
        }

        val nsn = maybeStripNationalPrefix(digits, meta)
        return PhoneNumber(meta.countryCode, nsn)
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

    private fun fromInternational(digits: String, defaultRegion: String): PhoneNumber =
        tryFromInternational(digits, defaultRegion)
            ?: throw NumberParseException(ErrorType.INVALID_COUNTRY_CODE, "No valid country calling code")

    private fun tryFromInternational(digits: String, defaultRegion: String): PhoneNumber? {
        // Country calling codes are 1–3 digits; take the shortest known match.
        for (len in 1..3) {
            if (digits.length <= len) break
            val cc = digits.substring(0, len).toIntOrNull() ?: continue
            if (cc !in COUNTRY_CODE_TO_MAIN_REGION) continue
            val rest = digits.substring(len)
            // libphonenumber runs national-prefix processing after country-code
            // extraction for international numbers too. The strip is guarded, so
            // it leaves already-viable numbers untouched but still applies
            // "local → full" transforms (e.g. NF's ([0-258]\d{4})$ → 3$1). Use the
            // default region's metadata when it shares this calling code, else the
            // code's main region.
            val region = if (GENERATED_METADATA[defaultRegion]?.countryCode == cc) {
                defaultRegion
            } else {
                COUNTRY_CODE_TO_MAIN_REGION[cc]
            }
            val regionMeta = region?.let { GENERATED_METADATA[it] }
            val nsn = if (regionMeta != null) maybeStripNationalPrefix(rest, regionMeta) else rest
            return PhoneNumber(cc, nsn)
        }
        return null
    }

    /**
     * Strip the national (trunk) prefix and apply any transform rule, faithfully
     * porting libphonenumber's `maybeStripNationalPrefixAndCarrierCode`. Returns
     * the national significant number (unchanged if no prefix applies, or if
     * stripping/transforming would make a previously-viable number non-viable).
     * Carrier-code capture is not retained (not needed for E.164).
     */
    private fun maybeStripNationalPrefix(number: String, meta: PhoneMetadata): String {
        // libphonenumber's metadata build defaults nationalPrefixForParsing to the
        // national prefix when absent; we replicate that fallback here.
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
