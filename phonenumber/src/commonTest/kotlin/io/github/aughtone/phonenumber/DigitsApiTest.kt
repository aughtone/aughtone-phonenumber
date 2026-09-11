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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The public digit API and the three libphonenumber-compatibility behaviours
 * (AOPH-2): full-Unicode digits by default with an opt-in compat flag (#4),
 * deterministic plus detection (#2), and non-leaking parse errors (#1).
 */
class DigitsApiTest {

    // --- decimalDigitValue -------------------------------------------------

    @Test fun asciiDigits() {
        for (d in 0..9) assertEquals(d, decimalDigitValue('0'.code + d))
    }

    @Test fun supplementaryDigit() {
        // U+1D7CE MATHEMATICAL BOLD DIGIT ZERO starts a supplementary Nd block.
        assertEquals(0, decimalDigitValue(0x1D7CE))
        assertEquals(7, decimalDigitValue(0x1D7CE + 7))
    }

    @Test fun nonDigitsAreNull() {
        assertNull(decimalDigitValue('a'.code))
        assertNull(decimalDigitValue('+'.code))
        assertNull(decimalDigitValue(0x002F)) // '/', just below the ASCII 0-9 block
        assertNull(decimalDigitValue(0x003A)) // ':', just above the ASCII 0-9 block
        assertNull(decimalDigitValue(0x1D800)) // in a gap above the supplementary math blocks
    }

    @Test fun frozenUnicodeVersionIsExposed() {
        assertEquals("17.0.0", DIGIT_UNICODE_VERSION)
        assertEquals(DIGIT_UNICODE_VERSION, PhoneNumberUtil.digitUnicodeVersion)
    }

    // --- normalizeDigitsOnly ----------------------------------------------

    @Test fun stripsNonDigitsKeepsAscii() {
        assertEquals("2015550123", normalizeDigitsOnly("(201) 555-0123"))
    }

    @Test fun supplementaryDigitsRecognisedByDefault() {
        // 𝟐𝟎𝟏𝟓𝟓𝟓𝟎𝟏𝟐𝟑 (mathematical bold), a supplementary Nd block.
        val bold = "𝟐𝟎𝟏𝟓𝟓" +
            "𝟓𝟎𝟏𝟐𝟑"
        assertEquals("2015550123", normalizeDigitsOnly(bold))
    }

    @Test fun compatStripsSupplementaryDigits() {
        val bold = "𝟐𝟎𝟏"
        assertEquals("201", normalizeDigitsOnly(bold)) // default: recognised
        assertEquals("", normalizeDigitsOnly(bold, libphonenumberCompat = true)) // upstream: stripped
    }

    // --- #4: parse accepts supplementary digits by default, compat rejects --

    @Test fun parseAcceptsSupplementaryDigitsByDefault() {
        val bold = "𝟐𝟎𝟏𝟓𝟓" +
            "𝟓𝟎𝟏𝟐𝟑"
        assertEquals("+12015550123", PhoneNumberUtil.parse(bold, "US").formatToE164())
    }

    @Test fun parseCompatRejectsSupplementaryOnlyInput() {
        val bold = "𝟐𝟎𝟏𝟓𝟓" +
            "𝟓𝟎𝟏𝟐𝟑"
        val e = assertFailsWith<PhoneNumberUtil.NumberParseException> {
            PhoneNumberUtil.parse(bold, "US", libphonenumberCompat = true)
        }
        assertEquals(PhoneNumberUtil.ErrorType.NOT_A_NUMBER, e.errorType)
    }

    // --- #2: plus detection is independent of leading formatting ----------

    @Test fun leadingExoticSpacingBeforePlusStillInternational() {
        // NBSP + narrow NBSP + regular space, then "+1 201 555 0123".
        assertEquals(
            "+12015550123",
            PhoneNumberUtil.parse("   +1 201 555 0123", "GB").formatToE164(),
        )
    }

    @Test fun leadingLettersBeforePlusStillInternational() {
        assertEquals(
            "+12015550123",
            PhoneNumberUtil.parse("call +1 201 555 0123", "GB").formatToE164(),
        )
    }

    // --- #1: exceptions never carry the input -----------------------------

    @Test fun parseErrorDoesNotLeakInput() {
        val secret = "no-digits-here-5eCr3T".filterNot { it.isDigit() } // strip so it's a true no-digit case
        val e = assertFailsWith<PhoneNumberUtil.NumberParseException> {
            PhoneNumberUtil.parse(secret, "US")
        }
        assertEquals(PhoneNumberUtil.ErrorType.NOT_A_NUMBER, e.errorType)
        assertTrue(e.message?.contains(secret) != true, "message must not echo the input")
    }

    @Test fun unknownRegionErrorDoesNotLeakInput() {
        val e = assertFailsWith<PhoneNumberUtil.NumberParseException> {
            PhoneNumberUtil.parse("+12015550123", "ZZ-secret-region")
        }
        assertEquals(PhoneNumberUtil.ErrorType.NOT_A_NUMBER, e.errorType)
        assertTrue(e.message?.contains("secret") != true, "message must not echo the region")
    }
}
