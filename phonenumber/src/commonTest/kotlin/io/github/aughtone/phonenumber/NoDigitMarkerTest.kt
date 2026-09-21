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
import kotlin.test.assertNull

/**
 * #23 / #24 — a marker with no valid extension digits must not corrupt an otherwise-valid number.
 * In the default (correct) mode the marker is dropped and the clean number returned; in compat mode
 * the upstream behaviour (keypad-fold for a dangling label; American-"#" split) is preserved exactly.
 * These are a deliberate default-mode divergence, so they are tested by our own expectations.
 */
class NoDigitMarkerTest {

    private fun e164(number: String, compat: Boolean = false) =
        PhoneNumberUtil.parse(number, "US", libphonenumberCompat = compat).formatToE164()

    private fun ext(number: String, compat: Boolean = false) =
        PhoneNumberUtil.parse(number, "US", libphonenumberCompat = compat).extension

    // --- #23: a dangling extension label ("ext") with no digits after it ------------------------

    @Test fun danglingLabelIsDroppedInDefaultMode() {
        // Default: "ext" is a dangling marker, not vanity letters — dropped, number left clean.
        assertEquals("+12125550123", e164("+1 212 555 0123 ext"))
        assertNull(ext("+1 212 555 0123 ext"))
        // Other explicit labels behave the same.
        assertEquals("+12125550123", e164("+1 212 555 0123 extn"))
        assertEquals("+12125550123", e164("+1 212 555 0123 extension"))
    }

    @Test fun danglingLabelFoldsLikeUpstreamInCompatMode() {
        // Compat: upstream keypad-folds the letters (e=3, x=9, t=8) into the number.
        assertEquals("+12125550123398", e164("+1 212 555 0123 ext", compat = true))
        assertNull(ext("+1 212 555 0123 ext", compat = true))
    }

    // --- #24: a trailing "#" that would take a subscriber group as the extension ----------------

    @Test fun trailingHashDoesNotEatSubscriberDigitsInDefaultMode() {
        // Default: dropping "0123" leaves +1212555 (invalid), so the digits belong to the number.
        assertEquals("+12125550123", e164("+1 212 555 0123#"))
        assertNull(ext("+1 212 555 0123#"))
    }

    @Test fun trailingHashSplitsLikeUpstreamInCompatMode() {
        // Compat: upstream's American "#" form takes the last group as the extension.
        assertEquals("+1212555", e164("+1 212 555 0123#", compat = true))
        assertEquals("0123", ext("+1 212 555 0123#", compat = true))
    }

    // --- Controls: genuine extensions and the sane single-char marker are unaffected ------------

    @Test fun genuineExtensionsAreUnaffected() {
        // The American "#" form is a post-dial string, not an extension (#30, ADR-0003) — the number is
        // still correct and the digits are captured, just in the right field.
        assertEquals("+12125550123", e164("+1 212 555 0123 12#"))
        assertEquals("12", PhoneNumberUtil.parse("+1 212 555 0123 12#", "US").postDialString)
        assertNull(ext("+1 212 555 0123 12#"))
        // A real labelled extension is unchanged.
        assertEquals("+12125550123", e164("+1 212 555 0123 x123"))
        assertEquals("123", ext("+1 212 555 0123 x123"))
        // Single-char "x" with no digits was already fine (not vanity, not a subscriber group).
        assertEquals("+12125550123", e164("+1 212 555 0123 x"))
        assertNull(ext("+1 212 555 0123 x"))
    }

    @Test fun genuineExtensionsUnaffectedInCompatModeToo() {
        assertEquals("12", ext("+1 212 555 0123 12#", compat = true))
        assertEquals("123", ext("+1 212 555 0123 x123", compat = true))
    }
}
