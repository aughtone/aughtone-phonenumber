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
import kotlin.test.assertNull

/**
 * #30 / ADR-0003 compat lock. Splitting post-dial from extension is a default-mode change only; with
 * `libphonenumberCompat = true` a `,`/`;`/`#`/`~` sequence must still come back as an extension,
 * byte-identical to libphonenumber, and `postDialString` must stay null. JVM-only.
 */
class PostDialParityTest {

    private val ref = ReferenceUtil.getInstance()

    @Test fun compatReturnsPostDialAsExtensionLikeUpstream() {
        val inputs = listOf(
            "+1 212 555 0123;4" to "US",
            "+1 212 555 0123,,4" to "US",
            "+1 212 555 0123~4" to "US",
            "+1 212 555 0123 12#" to "US",
            "+1 212 555 0123;ext=4" to "US",
            "+1 212 555 0123 x4" to "US",
        )
        for ((input, region) in inputs) {
            val refPn = ref.parse(input, region)
            val refE164 = ref.format(refPn, RefFormat.E164)
            val refExt = if (refPn.hasExtension()) refPn.extension else null

            val ourPn = PhoneNumberUtil.parse(input, region, libphonenumberCompat = true)
            assertEquals(refE164, ourPn.formatToE164(), "compat e164 for \"$input\"")
            assertEquals(refExt, ourPn.extension, "compat extension for \"$input\"")
            assertNull(ourPn.postDialString, "compat postDialString should be null for \"$input\"")
        }
    }
}
