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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs on every target; proves the matcher is correct AND identical across engines. */
class PhonePatternTest {

    // The cases that expose the Kotlin/Native + Kotlin/Wasm kotlin.text.Regex bug
    // (KT-89187). These MUST pass on every target with our own matcher.

    @Test fun unequalLengthAlternationBacktracks() {
        assertTrue(PhonePattern("(?:7|9\\d)\\d").matches("943"))
    }

    @Test fun curacaoGeneralPattern() {
        assertTrue(PhonePattern("(?:[34]1|60|(?:7|9\\d)\\d)\\d{5}").matches("94351234"))
    }

    // General correctness.

    @Test fun digitQuantifier() {
        assertTrue(PhonePattern("\\d{8}").matches("94351234"))
        assertFalse(PhonePattern("\\d{8}").matches("9435123"))  // too short
        assertFalse(PhonePattern("\\d{8}").matches("943512345")) // too long (anchored)
    }

    @Test fun charClassRangesAndNegation() {
        assertTrue(PhonePattern("[2-9]\\d{9}|3\\d{6}").matches("2015550123")) // US general
        assertTrue(PhonePattern("[2-9]\\d{9}|3\\d{6}").matches("3123456"))
        assertFalse(PhonePattern("[2-9]\\d{9}|3\\d{6}").matches("1015550123"))
        assertTrue(PhonePattern("[^0]").matches("5"))
        assertFalse(PhonePattern("[^0]").matches("0"))
    }

    @Test fun quantifiers() {
        assertTrue(PhonePattern("ab?c").matches("ac"))
        assertTrue(PhonePattern("ab?c").matches("abc"))
        assertTrue(PhonePattern("a{2,4}").matches("aaa"))
        assertFalse(PhonePattern("a{2,4}").matches("a"))
        assertTrue(PhonePattern("a{2,}").matches("aaaaa"))
    }

    @Test fun endAnchor() {
        assertTrue(PhonePattern("abc$").matches("abc"))
        // matchAtStart with a '$' branch: "([2-9]\d{6})$|1" — GB-style carrier/prefix pattern.
        val p = PhonePattern("([2-9]\\d{6})\$|1")
        val m = assertNotNull(p.matchAtStart("2015550"))
        assertEquals(7, m.end)
        assertEquals("2015550", m.groups[1])
    }

    @Test fun matchAtStartStripsAndCaptures() {
        // Strip national prefix "0" and capture the rest via a group.
        val m = assertNotNull(PhonePattern("0(\\d{2})").matchAtStart("0123456"))
        assertEquals(3, m.end)          // consumed "012"
        assertEquals("12", m.groups[1]) // captured "12"
    }

    @Test fun matchAtStartReturnsNullWhenNoLeadingMatch() {
        assertNull(PhonePattern("0").matchAtStart("123"))
    }

    @Test fun nonCapturingGroupHasNoCapture() {
        val m = assertNotNull(PhonePattern("(?:0)(\\d)").matchAtStart("05"))
        assertEquals(1, m.groups.size - 1) // exactly one capturing group
        assertEquals("5", m.groups[1])
    }
}
