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
 * #25 compat lock. The default-mode fix (run the Durchwahl guard even when an extension is present)
 * must NOT change compat behaviour: with `libphonenumberCompat = true` a Durchwahl number carrying an
 * extension still folds and keeps the extension, byte-identical to libphonenumber. JVM-only.
 */
class DurchwahlExtParityTest {

    private val ref = ReferenceUtil.getInstance()

    @Test fun compatMatchesUpstreamForDurchwahlWithExtension() {
        val cases = listOf(
            "+43 1 58058-0#4" to "AT",            // Durchwahl + extension
            "+43 1 58058-0" to "AT",              // baseline
            "+49 30 12345678-123456#4" to "DE",   // malformed resolve-to-base collision
            "+1 212 555 0123 x12" to "US",        // ordinary extension
            "+49 89 636 48018 x5" to "DE",        // space-grouped valid + extension
        )
        for ((input, region) in cases) {
            val refPn = ref.parse(input, region)
            val refE164 = ref.format(refPn, RefFormat.E164)
            val refExt = if (refPn.hasExtension()) refPn.extension else null

            val ourPn = PhoneNumberUtil.parse(input, region, libphonenumberCompat = true)
            assertEquals(refE164, ourPn.formatToE164(), "compat e164 for \"$input\" ($region)")
            assertEquals(refExt, ourPn.extension, "compat extension for \"$input\" ($region)")
        }
    }
}
