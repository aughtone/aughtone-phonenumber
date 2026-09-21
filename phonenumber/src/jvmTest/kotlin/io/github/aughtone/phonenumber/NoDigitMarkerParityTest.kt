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
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat as RefFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #23 / #24 compat lock. The default-mode fix (drop a no-digit marker instead of corrupting the
 * number) must NOT change compat behaviour: with `libphonenumberCompat = true` our output stays
 * byte-identical to libphonenumber. Verified against the reference for the two defect inputs and the
 * genuine-extension controls. JVM-only (needs the reference jar).
 */
class NoDigitMarkerParityTest {

    private val ref = ReferenceUtil.getInstance()

    @Test fun compatMatchesUpstreamForNoDigitMarkers() {
        val inputs = listOf(
            "+1 212 555 0123 ext",   // #23 — dangling label, upstream keypad-folds
            "+1 212 555 0123#",      // #24 — American "#" takes the last group
            "+1 212 555 0123 x",     // control
            "+1 212 555 0123 12#",   // real American extension
            "+1 212 555 0123 x123",  // real labelled extension
        )
        for (input in inputs) {
            val refPn = ref.parse(input, "US")
            val refE164 = ref.format(refPn, RefFormat.E164)
            val refExt = if (refPn.hasExtension()) refPn.extension else null

            val ourPn = PhoneNumberUtil.parse(input, "US", libphonenumberCompat = true)
            assertEquals(refE164, ourPn.formatToE164(), "compat e164 for \"$input\"")
            assertEquals(refExt, ourPn.extension, "compat extension for \"$input\"")
        }
    }
}
