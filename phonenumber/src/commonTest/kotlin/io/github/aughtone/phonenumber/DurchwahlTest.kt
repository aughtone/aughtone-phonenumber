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

/**
 * #6 the Durchwahl (direct-dial) ambiguity guard — a deliberate divergence from upstream, so it is
 * tested by our own expectations, not the parity harnesses (which run in compat mode).
 *
 * German/Austrian numbers spell a direct-dial extension with a hyphen or space (`+49 30 12345678-12`).
 * Upstream, and this port's compat mode, fold those digits into the national number, giving a
 * different, valid-looking number. In the default (correct) mode we refuse the number when it is valid
 * both with and without the trailing group, resolve to the base when only the base is valid, and
 * otherwise leave the number unchanged.
 */
class DurchwahlTest {

    private fun parse(number: String, region: String, compat: Boolean = false) =
        PhoneNumberUtil.parse(number, region, libphonenumberCompat = compat)

    @Test fun ambiguousTrailingGroupIsRefusedInDefaultMode() {
        // Only a HYPHEN-separated trailing group (the Durchwahl convention) on an otherwise-valid
        // number is treated as ambiguous — a space alone is not evidence (see [ordinaryNumbersAreUnaffected]).
        for ((input, region) in listOf(
            "+49 30 12345678-12" to "DE", // hyphen (Durchwahl)
            "+43 1 58058-0" to "AT", // Austrian Durchwahl
        )) {
            val e = assertFailsWith<PhoneNumberUtil.NumberParseException>(input) { parse(input, region) }
            // Its own error type, so callers can tell it apart from an ordinary non-number (#3 for normalize).
            assertEquals(PhoneNumberUtil.ErrorType.AMBIGUOUS_TRAILING_GROUP, e.errorType, input)
        }
    }

    @Test fun unicodeDashesAreTreatedAsTheHyphenSignal() {
        // #29: a typographic dash typed or autocorrected for a hyphen (en/em dash, minus, fullwidth
        // hyphen) is the Durchwahl signal too — otherwise it silently folds a wrong number.
        for (dash in listOf('–', '—', '−', '－')) {
            val input = "+43 1 58058${dash}0"
            val e = assertFailsWith<PhoneNumberUtil.NumberParseException>(input) { parse(input, "AT") }
            assertEquals(PhoneNumberUtil.ErrorType.AMBIGUOUS_TRAILING_GROUP, e.errorType, input)
        }
    }

    @Test fun compatModeFoldsLikeUpstream() {
        assertEquals("+49301234567812", parse("+49 30 12345678-12", "DE", compat = true).formatToE164())
        assertEquals("+431580580", parse("+43 1 58058-0", "AT", compat = true).formatToE164())
    }

    @Test fun unambiguousTrailingGroupResolvesToBase() {
        // Hyphen-separated, folded reading too long to be valid but the base valid → trailing group is
        // the direct-dial extension.
        val n = parse("+49 30 12345678-123456", "DE")
        assertEquals("+493012345678", n.formatToE164())
        assertEquals("123456", n.extension)
        // Same shape via a shorter hyphen group (Swiss).
        val ch = parse("+41 44 123 45 67-8", "CH")
        assertEquals("+41441234567", ch.formatToE164())
        assertEquals("8", ch.extension)
    }

    @Test fun durchwahlWithExtensionStillRefusesInDefaultMode() {
        // #25 — the ambiguity is a property of the number, not of a trailing extension, so a Durchwahl
        // number that also carries an extension marker must refuse exactly like the bare form.
        for ((input, region) in listOf(
            "+43 1 58058-0#4" to "AT",   // Austrian Durchwahl + a "#4" extension
            "+49 30 12345678-12#7" to "DE", // German Durchwahl + a "#7" extension
        )) {
            val e = assertFailsWith<PhoneNumberUtil.NumberParseException>(input) { parse(input, region) }
            assertEquals(PhoneNumberUtil.ErrorType.AMBIGUOUS_TRAILING_GROUP, e.errorType, input)
        }
        // Compat folds like upstream and keeps the explicit extension (no guard).
        val at = parse("+43 1 58058-0#4", "AT", compat = true)
        assertEquals("+431580580", at.formatToE164())
        assertEquals("4", at.extension)
    }

    @Test fun ordinaryExtensionsAreUnaffectedByTheGuard() {
        // #25 controls — a genuine extension on a non-ambiguous number is untouched in default mode.
        val us = parse("+1 212 555 0123 x12", "US")
        assertEquals("+12125550123", us.formatToE164())
        assertEquals("12", us.extension)
        // The #22 space-grouped valid number, now with an extension, still parses (space is not a signal).
        val de = parse("+49 89 636 48018 x5", "DE")
        assertEquals("+498963648018", de.formatToE164())
        assertEquals("5", de.extension)
    }

    @Test fun ordinaryNumbersAreUnaffected() {
        // No trailing-group ambiguity: a plainly formatted number still parses in default mode.
        assertEquals("+493012345678", parse("+49 30 12345678", "DE").formatToE164())
        // Regression: an ordinary space-grouped variable-length number whose leading part is itself a
        // valid number must NOT be refused — a space is not a Durchwahl separator (a hyphen is).
        assertEquals("+498963648018", parse("+49 89 636 48018", "DE").formatToE164())
        assertEquals("+49301234567812", parse("+49 30 12345678 12", "DE").formatToE164())
        // Fixed-length plans (US, GB) are safe because the folded reading is invalid there.
        assertEquals("+12125550123", parse("+1 (212) 555-0123", "US").formatToE164())
        assertEquals("+12125550123", parse("+1-212-555-0123", "US").formatToE164())
        assertEquals("+442071234567", parse("+44 20 7123 4567", "GB").formatToE164())
        // A hyphen inside an ordinary NZ number where the base alone is not valid.
        assertEquals("+6433316005", parse("03-331 6005", "NZ").formatToE164())
    }
}
