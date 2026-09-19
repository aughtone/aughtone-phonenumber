/*
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
 */
package io.github.aughtone.phonenumber

import com.google.i18n.phonenumbers.PhoneNumberUtil as ReferenceUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.NumberParseException as RefParseException
import kotlin.test.Test
import kotlin.test.fail

/**
 * Upstream-parity harness for AOPH-7. Feeds inputs drawn from libphonenumber's own
 * `PhoneNumberUtilTest` through this port in **compat mode**
 * (`libphonenumberCompat = true`) and asserts the result equals what the reference
 * library (pinned to the same metadata) produces. Faithful by construction: the
 * expected value is whatever upstream actually does, not a transcribed constant.
 *
 * Parity is checked on E.164 (which is "+" + country code + national significant
 * number, and — correctly — excludes any extension), plus throw-vs-parse agreement.
 * So a folded extension, a dropped `tel:` context, an unconverted alpha number, etc.
 * all surface as an E.164 mismatch.
 *
 * JVM-only: parity can only be checked where the reference exists. Cross-target
 * byte-stability is a separate property, covered by ConformanceTest.
 *
 * This runs in compat mode: it is the contract that "flip the flag and it IS
 * libphonenumber" holds. Every case is green (parse/validate/format scope, ADR-0001);
 * it now stands as a regression guard on that contract — a new failure means the port
 * has drifted from upstream and must be fixed before release.
 */
class UpstreamParityTest {

    private data class Case(val input: String, val region: String, val origin: String)

    // Inputs lifted verbatim from upstream PhoneNumberUtilTest. Grouped by the test
    // method they come from so a failure points back to the upstream source.
    private val cases = listOf(
        // --- testParseNationalNumber ---
        Case("033316005", "NZ", "testParseNationalNumber"),
        Case("33316005", "NZ", "testParseNationalNumber"),
        Case("03-331 6005", "NZ", "testParseNationalNumber"),
        Case("03 331 6005", "NZ", "testParseNationalNumber"),
        Case("0064 3 331 6005", "NZ", "testParseNationalNumber"),
        Case("01164 3 331 6005", "US", "testParseNationalNumber"),
        Case("+64 3 331 6005", "US", "testParseNationalNumber"),
        Case("+01164 3 331 6005", "US", "testParseNationalNumber"),
        Case("+0064 3 331 6005", "NZ", "testParseNationalNumber"),
        Case("+ 00 64 3 331 6005", "NZ", "testParseNationalNumber"),
        Case("64(0)64123456", "NZ", "testParseNationalNumber"),
        Case("301/23456", "DE", "testParseNationalNumber"),
        Case("123-456-7890", "US", "testParseNationalNumber"),
        Case("+81 *2345", "JP", "testParseNationalNumber(star)"),
        Case("12", "NZ", "testParseNationalNumber(short)"),
        // --- testParseNationalNumber, RFC3966 ---
        Case("tel:03-331-6005;phone-context=+64", "NZ", "testParseNationalNumber(rfc3966)"),
        Case("tel:331-6005;phone-context=+64-3", "US", "testParseNationalNumber(rfc3966)"),
        Case("My number is tel:03-331-6005;phone-context=+64", "NZ", "testParseNationalNumber(rfc3966)"),
        Case("tel:03-331-6005;isub=12345;phone-context=+64", "NZ", "testParseNationalNumber(rfc3966)"),
        Case("tel:+64-3-331-6005;isub=12345", "NZ", "testParseNationalNumber(rfc3966)"),
        Case("tel:253-0000;phone-context=www.google.com", "US", "testParseNationalNumber(rfc3966)"),
        // --- testParseWithLeadingZero ---
        Case("+39 02-36618 300", "NZ", "testParseWithLeadingZero"),
        Case("02-36618 300", "IT", "testParseWithLeadingZero"),
        Case("345 678 901", "IT", "testParseWithLeadingZero"),
        // --- testParseNumberWithAlphaCharacters ---
        Case("0800 DDA 005", "NZ", "testParseNumberWithAlphaCharacters"),
        Case("0900 DDA 6005", "NZ", "testParseNumberWithAlphaCharacters"),
        Case("0900 332 6005a", "NZ", "testParseNumberWithAlphaCharacters"),
        Case("0900 a332 600A5", "NZ", "testParseNumberWithAlphaCharacters"),
        // --- testParseExtensions ---
        Case("03 331 6005 ext 3456", "NZ", "testParseExtensions"),
        Case("03-3316005x3456", "NZ", "testParseExtensions"),
        Case("+44 2034567890x456", "GB", "testParseExtensions"),
        Case("+44-2034567890;ext=456", "GB", "testParseExtensions"),
        Case("(800) 901-3355 x 7246433", "US", "testParseExtensions"),
        Case("(800) 901-3355 , ext 7246433", "US", "testParseExtensions"),
        Case("tel:2034567890;ext=456;phone-context=+44", "ZZ", "testParseExtensions"),
        // --- testFailedParseOnInvalidNumbers (expected: throw, with a specific ErrorType) ---
        // The reference defines the expected ErrorType; we compare against it. Upstream
        // has five ErrorTypes (NOT_A_NUMBER, TOO_LONG, TOO_SHORT_NSN, TOO_SHORT_AFTER_IDD,
        // INVALID_COUNTRY_CODE); this port currently has two.
        Case("This is not a phone number", "NZ", "testFailedParseOnInvalidNumbers"),
        Case("1 Still not a number", "NZ", "testFailedParseOnInvalidNumbers"),
        Case("1 MICROSOFT", "NZ", "testFailedParseOnInvalidNumbers"),
        Case("12 MICROSOFT", "NZ", "testFailedParseOnInvalidNumbers"),
        Case("01495 72553301873 810104", "GB", "testFailedParseOnInvalidNumbers(TOO_LONG)"),
        Case("+---", "DE", "testFailedParseOnInvalidNumbers"),
        Case("+***", "DE", "testFailedParseOnInvalidNumbers"),
        Case("+*******91", "DE", "testFailedParseOnInvalidNumbers"),
        Case("+49 0", "DE", "testFailedParseOnInvalidNumbers(TOO_SHORT_NSN)"),
        Case("+210 3456 56789", "NZ", "testFailedParseOnInvalidNumbers(INVALID_CC)"),
        Case("+ 00 210 3 331 6005", "NZ", "testFailedParseOnInvalidNumbers(INVALID_CC)"),
        Case("123 456 7890", "ZZ", "testFailedParseOnInvalidNumbers(unknownRegion)"),
        Case("123 456 7890", "CS", "testFailedParseOnInvalidNumbers(deprecatedRegion)"),
        Case("0044------", "GB", "testFailedParseOnInvalidNumbers(TOO_SHORT_AFTER_IDD)"),
        Case("0044", "GB", "testFailedParseOnInvalidNumbers(TOO_SHORT_AFTER_IDD)"),
        Case("011", "US", "testFailedParseOnInvalidNumbers(TOO_SHORT_AFTER_IDD)"),
        // --- testParseWithInternationalPrefixes ---
        Case("+1 (650) 253-0000", "NZ", "testParseWithInternationalPrefixes"),
        Case("011 800 1234 5678", "US", "testParseWithInternationalPrefixes"),
        Case("1-650-253-0000", "US", "testParseWithInternationalPrefixes"),
        Case("0011-650-253-0000", "SG", "testParseWithInternationalPrefixes"),
        Case("0081-650-253-0000", "SG", "testParseWithInternationalPrefixes"),
        Case("0191-650-253-0000", "SG", "testParseWithInternationalPrefixes"),
        Case("0~01-650-253-0000", "PL", "testParseWithInternationalPrefixes"),
        Case("++1 (650) 253-0000", "PL", "testParseWithInternationalPrefixes"),
        // --- testParseItalianLeadingZeros (leading 0 kept, not stripped as national prefix) ---
        Case("011", "AU", "testParseItalianLeadingZeros"),
        Case("001", "AU", "testParseItalianLeadingZeros"),
        Case("000", "AU", "testParseItalianLeadingZeros"),
        Case("0000", "AU", "testParseItalianLeadingZeros"),
        // --- testParseMaliciousInput (expect TOO_LONG; also a DoS-shape input) ---
        Case("+".repeat(6000) + "12222-33-244 extensioB 343+", "US", "testParseMaliciousInput"),
        Case("200".repeat(350) + " extensiOB 345", "US", "testParseMaliciousInput"),
        // --- testParseNumbersWithPlusWithNoRegion (ZZ/unknown region allowed when + present) ---
        Case("+64 3 331 6005", "ZZ", "testParseNumbersWithPlusWithNoRegion"),
        Case("＋64 3 331 6005", "ZZ", "testParseNumbersWithPlusWithNoRegion"),
        Case("Tel: +64 3 331 6005", "ZZ", "testParseNumbersWithPlusWithNoRegion"),
        Case("+800 1234 5678", "ZZ", "testParseNumbersWithPlusWithNoRegion"),
        Case("tel:03-331-6005;phone-context=+64", "ZZ", "testParseNumbersWithPlusWithNoRegion"),
        // --- testParseWithXInNumber (carrier-selection 'xx' and (0) handling) ---
        Case("01187654321", "AR", "testParseWithXInNumber"),
        Case("(0) 1187654321", "AR", "testParseWithXInNumber"),
        Case("0 1187654321", "AR", "testParseWithXInNumber"),
        Case("(0xx) 1187654321", "AR", "testParseWithXInNumber"),
        Case("011xx5481429712", "US", "testParseWithXInNumber"),
        // --- testParseNonAscii (full-width, soft hyphen, CJK space, U+30FC dash, Mongolian digits) ---
        Case("＋1 (650) 253-0000", "SG", "testParseNonAscii"),
        Case("1 (650) 253­-0000", "US", "testParseNonAscii"),
        Case("＋１　（６５０）　２５３－００００", "SG", "testParseNonAscii"),
        Case("＋１　（６５０）　２５３ー００００", "SG", "testParseNonAscii"),
        Case("᠑ ᠖᠕᠐ ᠒᠕᠓ ᠐᠐᠐᠐", "US", "testParseNonAscii"),
        // --- testParseNationalNumberArgentina ---
        Case("+54 9 343 555 1212", "AR", "testParseNationalNumberArgentina"),
        Case("0343 15 555 1212", "AR", "testParseNationalNumberArgentina"),
        Case("+54 9 3715 65 4320", "AR", "testParseNationalNumberArgentina"),
        Case("03715 15 65 4320", "AR", "testParseNationalNumberArgentina"),
        Case("911 876 54321", "AR", "testParseNationalNumberArgentina"),
        Case("+54 11 8765 4321", "AR", "testParseNationalNumberArgentina"),
        Case("011 8765 4321", "AR", "testParseNationalNumberArgentina"),
        Case("+54 3715 65 4321", "AR", "testParseNationalNumberArgentina"),
        Case("03715 65 4321", "AR", "testParseNationalNumberArgentina"),
        Case("+54 23 1234 0000", "AR", "testParseNationalNumberArgentina"),
        Case("023 1234 0000", "AR", "testParseNationalNumberArgentina"),
        // --- testParseNumbersMexico ---
        Case("+52 (449)978-0001", "MX", "testParseNumbersMexico"),
        Case("01 (449)978-0001", "MX", "testParseNumbersMexico"),
        Case("(449)978-0001", "MX", "testParseNumbersMexico"),
        Case("+52 1 33 1234-5678", "MX", "testParseNumbersMexico"),
        Case("044 (33) 1234-5678", "MX", "testParseNumbersMexico"),
        Case("045 33 1234-5678", "MX", "testParseNumbersMexico"),
        // --- testParseNumberTooShortIfNationalPrefixStripped (BY possible-length strip guard) ---
        Case("8123", "BY", "testParseNumberTooShortIfNationalPrefixStripped"),
        Case("81234", "BY", "testParseNumberTooShortIfNationalPrefixStripped"),
        Case("812345", "BY", "testParseNumberTooShortIfNationalPrefixStripped"),
        Case("8123456", "BY", "testParseNumberTooShortIfNationalPrefixStripped"),
        // --- testParseWithPhoneContext, valid (RFC3966 grammar) ---
        Case("tel:033316005;phone-context=+64", "ZZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=+64;{this isn't part of phone-context anymore!}", "ZZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=+64-3", "ZZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=+(555)", "ZZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=+-1-2.3()", "ZZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=abc.nz", "NZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=www.PHONE-numb3r.com", "NZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=a", "NZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=3phone.J.", "NZ", "testParseWithPhoneContext"),
        Case("tel:033316005;phone-context=a--z", "NZ", "testParseWithPhoneContext"),
        // --- testParseWithPhoneContext, invalid descriptor (expect throw) ---
        Case("tel:033316005;phone-context=", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=+", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=64", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=++64", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=+abc", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=.", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=3phone", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=a-.nz", "NZ", "testParseWithPhoneContext(invalid)"),
        Case("tel:033316005;phone-context=a{b}c", "NZ", "testParseWithPhoneContext(invalid)"),
        // --- testParseHandles*Extensions* (more extension spellings; all base E.164) ---
        Case("tel:+6433316005;ext=0", "NZ", "testParseHandlesLongExtensionsWithExplicitLabels"),
        Case("03 3316005ext:1", "NZ", "testParseHandlesLongExtensionsWithExplicitLabels"),
        Case("03 3316005 x 123456789", "NZ", "testParseHandlesShortExtensionsWithAmbiguousChar"),
        Case("03 3316005 #123456789#", "NZ", "testParseHandlesShortExtensionsWithAmbiguousChar"),
        Case("+12679000000,,123456789012345#", "US", "testParseHandlesLongExtensionsWithAutoDiallingLabels"),
        Case("+442034000000,,123456789#", "GB", "testParseHandlesLongExtensionsWithAutoDiallingLabels"),
        Case("+1123-456-7890 666666#", "US", "testParseHandlesShortExtensionsWhenNotSureOfLabel"),
        // Note: upstream's null-region and parseAndKeepRawInput cases are omitted — this port's
        // `defaultRegion` is a non-null String and it has no raw-input API. Documented, not skipped.
    )

    // e164 for a successful parse; errorType (name) for a throw. Faithful either way:
    // the reference supplies whichever it does, and we require ours to agree.
    private data class Outcome(val e164: String?, val threw: Boolean, val errorType: String?)

    private fun reference(input: String, region: String): Outcome {
        val ref = ReferenceUtil.getInstance()
        return try {
            Outcome(ref.format(ref.parse(input, region), PhoneNumberFormat.E164), false, null)
        } catch (e: RefParseException) {
            Outcome(null, true, e.errorType.name)
        }
    }

    private fun ours(input: String, region: String): Outcome = try {
        Outcome(PhoneNumberUtil.parse(input, region, libphonenumberCompat = true).formatToE164(), false, null)
    } catch (e: PhoneNumberUtil.NumberParseException) {
        Outcome(null, true, e.errorType.name)
    }

    @Test
    fun compatModeMatchesUpstream() {
        val gaps = mutableListOf<String>()
        var green = 0
        for (c in cases) {
            val ref = reference(c.input, c.region)
            val us = ours(c.input, c.region)
            val agree = when {
                ref.threw != us.threw -> false
                ref.threw -> ref.errorType == us.errorType // both threw: types must match
                else -> ref.e164 == us.e164               // both parsed: E.164 must match
            }
            if (agree) {
                green++
            } else {
                val refStr = if (ref.threw) "THROW(${ref.errorType})" else ref.e164
                val usStr = if (us.threw) "THROW(${us.errorType})" else us.e164
                gaps += "[${c.origin}] \"${c.input}\" (${c.region}): ours=$usStr  ref=$refStr"
            }
        }
        val summary = "Upstream parity (compat mode): $green/${cases.size} match; ${gaps.size} gap(s)."
        println(summary)
        gaps.forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.joinToString("\n"))
    }
}
