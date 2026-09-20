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
 * #27 / ADR-0002 compat lock. The default-mode alpha changes (refuse vanity, treat an unrecognised
 * label as an extension) must NOT change compat behaviour: with `libphonenumberCompat = true` our
 * output stays byte-identical to libphonenumber, and the new `allowVanityNumbers` / `extensionMarkers`
 * parameters have no effect in compat mode. JVM-only.
 */
class VanityParityTest {

    private val ref = ReferenceUtil.getInstance()

    // (e164, extension, threw) — a compat parse either yields the same output as upstream or, when
    // upstream rejects the input, also throws. Non-ASCII vanity (fullwidth, CJK) is the throwing case:
    // upstream's VALID_PHONE_NUMBER accepts ASCII letters but no other script.
    private fun refOutcome(input: String, region: String): Triple<String, String?, Boolean> =
        runCatching {
            val p = ref.parse(input, region)
            Triple(ref.format(p, RefFormat.E164), if (p.hasExtension()) p.extension else null, false)
        }.getOrElse { Triple("", null, true) }

    private fun ourCompatOutcome(input: String, region: String, markers: Set<String>?): Triple<String, String?, Boolean> =
        runCatching {
            val p = PhoneNumberUtil.parse(input, region, libphonenumberCompat = true, allowVanityNumbers = false, extensionMarkers = markers)
            Triple(p.formatToE164(), p.extension, false)
        }.getOrElse { Triple("", null, true) }

    @Test fun compatMatchesUpstreamForAlphaInput() {
        val inputs = listOf(
            "1-800-FLOWERS" to "US",            // vanity — upstream keypad-folds
            "+1 212 555 0123 poste 4" to "US",  // unrecognised label — upstream folds "poste"
            "+1 212 555 0123 anexo 4" to "US",  // recognised label — extension
            "1800 FLOWERS" to "US",
            "+1 (800) FLOWERS" to "US",
            "1-800-FLOWERS ext 123" to "US",    // vanity + extension
            "1300 FLOWERS" to "AU",             // non-US vanity plan
            "0800 FLOWERS" to "GB",
            "1-800-ＦＬＯＷＥＲＳ" to "US", // fullwidth Latin → upstream NOT_A_NUMBER
            "+1 212 555 0123 分机 4" to "US",                    // CJK label → upstream NOT_A_NUMBER
        )
        for ((input, region) in inputs) {
            val ref = refOutcome(input, region)
            // The new params must not change compat, so check with and without them.
            for (markers in listOf<Set<String>?>(null, setOf("poste", "ramal"))) {
                assertEquals(ref, ourCompatOutcome(input, region, markers), "compat parity for \"$input\" ($region), markers=$markers")
            }
        }
    }
}
