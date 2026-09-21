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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #27 / ADR-0002 — default-mode alphabetic input. A run of letters is never keypad-folded into the
 * number: letters after an already-valid number are an unrecognised extension marker (dropped, trailing
 * digits become the extension), and letters the number needs to be valid are a vanity number, refused
 * unless the caller opts in. Callers with known data can supply extra markers. A deliberate default-mode
 * divergence, so tested by our own expectations; compat behaviour is pinned in the jvm parity harness.
 */
class VanityAndExtensionMarkerTest {

    private fun parse(number: String, region: String = "US", vanity: Boolean = false, unknown: Boolean = false, markers: Set<String>? = null) =
        PhoneNumberUtil.parse(number, region, allowVanityNumbers = vanity, allowUnknownExtensions = unknown, extensionMarkers = markers)

    // --- Unrecognised extension label after a valid number (#26/#27) -----------------------------

    @Test fun unrecognisedLabelAfterValidNumberIsDroppedByDefault() {
        // ASCII, non-ASCII (Cyrillic/CJK) and accented labels alike: the number before them is valid, so
        // the marker is dropped with its trailing digits (never folded), leaving the correct number, no
        // extension, and the discarded run recorded in droppedText. Script-agnostic. (#26/#27, ADR-0002)
        for (label in listOf("poste", "ramal", "Durchwahl", "внутр", "分机", "tél")) {
            val n = parse("+1 212 555 0123 $label 4")
            assertEquals("+12125550123", n.formatToE164(), label)
            assertNull(n.extension, label)
            assertEquals("$label 4", n.droppedText, label)
        }
        // Even with allowVanityNumbers on, a marker after a VALID number is an extension marker (dropped),
        // not folded — the gating regression Gemini caught.
        val v = parse("+1 212 555 0123 poste 4", vanity = true)
        assertEquals("+12125550123", v.formatToE164())
        assertNull(v.extension)
        // A recognised built-in label still yields its extension (regression).
        assertEquals("4", parse("+1 212 555 0123 anexo 4").extension)
    }

    @Test fun isNumberMatchTreatsUnknownLabelAsMarkerNotAFold() {
        // Regression (Gemini): isNumberMatch parses with vanity conversion on, so the unknown label must
        // still be treated as a marker (dropped), not keypad-folded — otherwise the two would not match.
        assertEquals(
            PhoneNumberUtil.MatchType.EXACT_MATCH,
            PhoneNumberUtil.isNumberMatch("+1 212 555 0123", "+1 212 555 0123 poste 4"),
        )
    }

    @Test fun unrecognisedLabelExtractsOnlyWhenAllowUnknownExtensions() {
        // Opt-in: take the trailing digits after an unknown marker as the extension (best-effort).
        val n = parse("+1 212 555 0123 poste 4", unknown = true)
        assertEquals("+12125550123", n.formatToE164())
        assertEquals("4", n.extension)
        // No trailing digits → still just the clean number, no extension.
        val m = parse("+1 212 555 0123 poste", unknown = true)
        assertEquals("+12125550123", m.formatToE164())
        assertNull(m.extension)
    }

    // --- Vanity numbers refused by default, opt-in to convert -----------------------------------

    @Test fun vanityNumberIsRefusedByDefault() {
        // The letters are needed to form the number (1800 alone is not valid) → vanity → refused.
        val e = assertFailsWith<PhoneNumberUtil.NumberParseException> { parse("1-800-FLOWERS") }
        assertEquals(PhoneNumberUtil.ErrorType.ALPHA_NUMBER_DISALLOWED, e.errorType)
    }

    @Test fun vanityNumberConvertsWhenOptedIn() {
        assertEquals("+18003569377", parse("1-800-FLOWERS", vanity = true).formatToE164())
    }

    @Test fun isValidMirrorsTheVanityPolicy() {
        assertFalse(PhoneNumberUtil.isValid("1-800-FLOWERS", "US"))
        assertTrue(PhoneNumberUtil.isValid("1-800-FLOWERS", "US", allowVanityNumbers = true))
        // An ordinary number is unaffected by the flag.
        assertTrue(PhoneNumberUtil.isValid("+16502530000", "US"))
    }

    // --- Caller-provided extension markers ------------------------------------------------------

    @Test fun callerMarkerStripsOnANotIndependentlyValidBase() {
        // "1234" is not a valid number on its own, so the validity heuristic can't tell "poste" is a
        // marker — but a caller working with known data can say so.
        val n = parse("1234 poste 5", markers = setOf("poste"))
        assertEquals("5", n.extension)
        // Matching is case-insensitive.
        assertEquals("5", parse("1234 POSTE 5", markers = setOf("poste")).extension)
    }

    @Test fun withoutTheMarkerTheSameInputIsRefused() {
        val e = assertFailsWith<PhoneNumberUtil.NumberParseException> { parse("1234 poste 5") }
        assertEquals(PhoneNumberUtil.ErrorType.ALPHA_NUMBER_DISALLOWED, e.errorType)
    }

    // --- Regressions: earlier fixes and ordinary numbers unaffected -----------------------------

    @Test fun earlierBehavioursUnaffected() {
        // #23 dangling recognised label, #24 trailing '#', ordinary number.
        assertEquals("+12125550123", parse("+1 212 555 0123 ext").formatToE164())
        assertEquals("+12125550123", parse("+1 212 555 0123#").formatToE164())
        assertEquals("+16502530000", parse("(650) 253-0000").formatToE164())
        // #25 Durchwahl + extension still refuses.
        val e = assertFailsWith<PhoneNumberUtil.NumberParseException> { parse("+43 1 58058-0#4", "AT") }
        assertEquals(PhoneNumberUtil.ErrorType.AMBIGUOUS_TRAILING_GROUP, e.errorType)
    }

    // --- Compat mode keeps upstream's keypad fold (byte-stable across targets) -------------------

    @Test fun compatFoldsLikeUpstream() {
        assertEquals("+18003569377", PhoneNumberUtil.parse("1-800-FLOWERS", "US", libphonenumberCompat = true).formatToE164())
        // An unrecognised label folds too, and caller markers are ignored under compat.
        val n = PhoneNumberUtil.parse("+1 212 555 0123 poste 4", "US", libphonenumberCompat = true, extensionMarkers = setOf("poste"))
        assertEquals("+12125550123767834", n.formatToE164())
        assertNull(n.extension)
    }
}
