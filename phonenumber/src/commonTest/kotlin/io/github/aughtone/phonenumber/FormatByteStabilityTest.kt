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

import io.github.aughtone.phonenumber.PhoneNumberUtil.PhoneNumberFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-target byte-stability of `format()`. The JVM `FormatParityTest` checks agreement with the
 * reference, but only on the JVM; this pins the NATIONAL / INTERNATIONAL / RFC3966 / E164 output on
 * every target (jvm, wasmJs, native, …) so the deterministic [PhonePattern] engine keeps producing
 * identical formatted strings everywhere. Expected values confirmed against libphonenumber 9.0.39.
 */
class FormatByteStabilityTest {

    private data class Expect(
        val e164: String,
        val region: String,
        val national: String,
        val international: String,
        val rfc3966: String,
    )

    private val cases = listOf(
        Expect("+16502530000", "US", "(650) 253-0000", "+1 650-253-0000", "tel:+1-650-253-0000"),
        Expect("+442083661177", "GB", "020 8366 1177", "+44 20 8366 1177", "tel:+44-20-8366-1177"),
        Expect("+390236618300", "IT", "02 3661 8300", "+39 02 3661 8300", "tel:+39-02-3661-8300"),
        Expect("+81312345678", "JP", "03-1234-5678", "+81 3-1234-5678", "tel:+81-3-1234-5678"),
        Expect("+61212345678", "AU", "(02) 1234 5678", "+61 2 1234 5678", "tel:+61-2-1234-5678"),
    )

    @Test fun formatIsByteStableAcrossTargets() {
        for (c in cases) {
            val n = PhoneNumberUtil.parse(c.e164, c.region)
            assertEquals(c.e164, PhoneNumberUtil.format(n, PhoneNumberFormat.E164), "E164 ${c.e164}")
            assertEquals(c.national, PhoneNumberUtil.format(n, PhoneNumberFormat.NATIONAL), "NATIONAL ${c.e164}")
            assertEquals(c.international, PhoneNumberUtil.format(n, PhoneNumberFormat.INTERNATIONAL), "INTERNATIONAL ${c.e164}")
            assertEquals(c.rfc3966, PhoneNumberUtil.format(n, PhoneNumberFormat.RFC3966), "RFC3966 ${c.e164}")
        }
    }
}
