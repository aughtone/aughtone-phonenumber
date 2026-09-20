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
 * #30 / ADR-0003 — extension vs post-dial (RFC 3966 `;ext=` vs `;postd=`). In the default mode a
 * `,`/`;`/`#`/`~`-introduced sequence is a post-dial string (`PhoneNumber.postDialString`), not an
 * extension; `;ext=` and the word labels remain extensions. A deliberate default-mode divergence, so
 * tested by our own expectations; compat behaviour (everything as an extension) is pinned in the jvm
 * parity harness. Neither field is part of `formatToE164()`.
 */
class PostDialTest {

    private fun parse(number: String, region: String = "US") = PhoneNumberUtil.parse(number, region)

    @Test fun postDialMarkersPopulatePostDialString() {
        // `;`, `,,`, `~`, and the American "digits then #" form are post-dial, not extension.
        for (input in listOf("+1 212 555 0123;4", "+1 212 555 0123,,4", "+1 212 555 0123~4", "+1 212 555 0123 12#")) {
            val n = parse(input)
            assertEquals("+12125550123", n.formatToE164(), input)
            assertNull(n.extension, input)
            assertEquals(if (input.contains("12#")) "12" else "4", n.postDialString, input)
        }
    }

    @Test fun extensionMarkersStayExtensions() {
        // `;ext=`, the word labels, and `x` are extensions, not post-dial.
        for (input in listOf("+1 212 555 0123;ext=4", "+1 212 555 0123 ext 4", "+1 212 555 0123 anexo 4", "+1 212 555 0123 x4")) {
            val n = parse(input)
            assertEquals("+12125550123", n.formatToE164(), input)
            assertEquals("4", n.extension, input)
            assertNull(n.postDialString, input)
        }
    }

    @Test fun bareHashStillFoldsWithNeitherField() {
        // #24: a trailing "#" that would eat a subscriber group folds back into the number — no extension,
        // no post-dial.
        val n = parse("+1 212 555 0123#")
        assertEquals("+12125550123", n.formatToE164())
        assertNull(n.extension)
        assertNull(n.postDialString)
    }

    @Test fun durchwahlWithPostDialStillRefuses() {
        // #25: the ambiguity is a property of the number; a trailing post-dial marker does not excuse it.
        val e = kotlin.test.assertFailsWith<PhoneNumberUtil.NumberParseException> { parse("+43 1 58058-0#4", "AT") }
        assertEquals(PhoneNumberUtil.ErrorType.AMBIGUOUS_TRAILING_GROUP, e.errorType)
    }

    @Test fun compatReturnsPostDialAsExtension() {
        // Compat is byte-identical to upstream, which returns these as an extension; postDialString stays null.
        for (input in listOf("+1 212 555 0123;4", "+1 212 555 0123 12#")) {
            val n = PhoneNumberUtil.parse(input, "US", libphonenumberCompat = true)
            assertEquals(if (input.contains("12#")) "12" else "4", n.extension, input)
            assertNull(n.postDialString, input)
        }
    }
}
