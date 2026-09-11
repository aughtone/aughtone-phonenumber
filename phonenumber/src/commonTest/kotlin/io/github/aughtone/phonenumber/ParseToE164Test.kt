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
import kotlin.test.assertTrue

class ParseToE164Test {

    private fun e164(number: String, region: String) =
        PhoneNumberUtil.parse(number, region).formatToE164()

    @Test
    fun usNationalFormat() {
        assertEquals("+12015550123", e164("(201) 555-0123", "US"))
    }

    @Test
    fun usWithNationalPrefix() {
        assertEquals("+12015550123", e164("1 201 555 0123", "US"))
    }

    @Test
    fun usInternationalPlus() {
        assertEquals("+12015550123", e164("+1 201-555-0123", "US"))
    }

    @Test
    fun gbNationalFormatStripsTrunkZero() {
        assertEquals("+441212345678", e164("0121 234 5678", "GB"))
    }

    @Test
    fun gbInternationalPlus() {
        assertEquals("+441212345678", e164("+44 121 234 5678", "GB"))
    }

    @Test
    fun plusFormIgnoresDefaultRegion() {
        // Country code comes from the "+", not the default region.
        assertEquals("+441212345678", e164("+44 121 234 5678", "US"))
    }

    @Test
    fun iddDialedInternationalFromUs() {
        // 011 is the US IDD; the rest is a GB number.
        assertEquals("+441212345678", e164("011 44 121 234 5678", "US"))
    }

    @Test
    fun validAndInvalid() {
        assertTrue(PhoneNumberUtil.isValid("(201) 555-0123", "US"))
        assertTrue(PhoneNumberUtil.isValid("+441212345678", "US"))
        assertFalse(PhoneNumberUtil.isValid("12", "US"))
    }

    @Test
    fun metadataEpochExposed() {
        assertEquals("9.0.39", PhoneNumberUtil.metadataVersion)
    }
}
