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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** #5 extension parsing, cross-target (deterministic expected values; no reference needed). */
class ExtensionParseTest {

    private fun parse(number: String, region: String) = PhoneNumberUtil.parse(number, region)

    @Test fun markerFormsCaptureExtension() {
        for ((input, region) in listOf(
            "+44 2034567890x456" to "GB",
            "+44-2034567890;ext=456" to "GB",
        )) {
            val n = parse(input, region)
            assertEquals("+442034567890", n.formatToE164(), input)
            assertEquals("456", n.extension, input)
        }
    }

    @Test fun labelAndSymbolExtensions() {
        val nz = parse("03 331 6005 ext 3456", "NZ")
        assertEquals("+6433316005", nz.formatToE164())
        assertEquals("3456", nz.extension)

        val us = parse("(800) 901-3355 x 7246433", "US")
        assertEquals("+18009013355", us.formatToE164())
        assertEquals("7246433", us.extension)

        // A "#…#" sequence is a post-dial string (DTMF), not an extension (#30, ADR-0003).
        val hashed = parse("03 3316005 #123456789#", "NZ")
        assertEquals("+6433316005", hashed.formatToE164())
        assertEquals("123456789", hashed.postDialString)
        assertNull(hashed.extension)
    }

    @Test fun nonAsciiExtensionDigitsFoldToAscii() {
        // Arabic-Indic and Devanagari extension digits are recognised and folded to ASCII, so the
        // extension is byte-stable across scripts (a deliberate divergence from upstream, which keeps
        // the raw characters). Without this, "ext ٤" would fall through to vanity conversion and fold
        // into the national number. Both modes fold; the extension is never part of the E.164.
        val arabicIndic = parse("+1 212 555 0123 ext ٤", "US") // ٤ = 4
        assertEquals("+12125550123", arabicIndic.formatToE164())
        assertEquals("4", arabicIndic.extension)

        val devanagari = parse("03 3316005 x २३", "NZ") // २३ = 23
        assertEquals("+6433316005", devanagari.formatToE164())
        assertEquals("23", devanagari.extension)

        val compat = PhoneNumberUtil.parse("+1 212 555 0123 ext ٤", "US", libphonenumberCompat = true)
        assertEquals("+12125550123", compat.formatToE164())
        assertEquals("4", compat.extension)
    }

    @Test fun supplementaryExtensionDigitsFollowTheModeSplit() {
        // U+1D7D2 MATHEMATICAL BOLD DIGIT FOUR — a supplementary-plane Nd digit.
        val bold4 = "𝟒"
        // Default mode folds every Nd block, including supplementary, exactly like the national number.
        val def = parse("+1 212 555 0123 ext $bold4", "US")
        assertEquals("+12125550123", def.formatToE164())
        assertEquals("4", def.extension)
        // Compat mode is BMP-only (matching the number): the supplementary digit is not folded into
        // the extension.
        val compatExt = runCatching {
            PhoneNumberUtil.parse("+1 212 555 0123 ext $bold4", "US", libphonenumberCompat = true).extension
        }.getOrNull()
        assertNotEquals("4", compatExt)
    }

    @Test fun carrierCodeIsNotAnExtension() {
        // "xx" is a carrier-selection code; the 10 digits after exceed the ambiguous-marker cap,
        // so this must parse as one number with no extension.
        val n = parse("011xx5481429712", "US")
        assertEquals("+5481429712", n.formatToE164())
        assertNull(n.extension)
    }
}
