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
import kotlin.test.assertTrue

/**
 * #18 value object. Ports the intent of upstream's PhonenumberTest to this port's immutable
 * model: equality is by fields, the optional fields default to unset, and none of them changes
 * the E.164 form. (Upstream's mutable-builder operations — mergeFrom, clear, setters — have no
 * equivalent in an immutable value object; that is an intentional API divergence, not a gap.)
 */
class PhoneNumberValueTest {

    @Test fun defaultsAreUnset() {
        val n = PhoneNumber(64, "33316005")
        assertNull(n.extension)
        assertNull(n.rawInput)
        assertNull(n.preferredDomesticCarrierCode)
        assertEquals(false, n.italianLeadingZero)
        assertEquals(1, n.numberOfLeadingZeros)
        assertEquals(CountryCodeSource.UNSPECIFIED, n.countryCodeSource)
    }

    @Test fun equalityIsByFields() {
        assertEquals(PhoneNumber(64, "33316005"), PhoneNumber(64, "33316005"))
        assertNotEquals(PhoneNumber(64, "33316005"), PhoneNumber(64, "33316005", extension = "12"))
        assertNotEquals(PhoneNumber(1, "2015550123"), PhoneNumber(64, "2015550123"))
        // Same base, different leading-zero metadata are different numbers.
        assertNotEquals(
            PhoneNumber(39, "236618300"),
            PhoneNumber(39, "236618300", italianLeadingZero = true),
        )
    }

    @Test fun extensionIsNotPartOfE164() {
        val base = PhoneNumber(44, "2034567890")
        val withExt = PhoneNumber(44, "2034567890", extension = "456")
        assertEquals("+442034567890", base.formatToE164())
        assertEquals("+442034567890", withExt.formatToE164())
        assertEquals("456", withExt.extension)
    }

    @Test fun copyCarriesAndOverridesFields() {
        val n = PhoneNumber(1, "2125550123").copy(extension = "9", countryCodeSource = CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN)
        assertEquals("9", n.extension)
        assertEquals(CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN, n.countryCodeSource)
        assertEquals(1, n.countryCode)
        assertTrue(n.formatToE164() == "+12125550123")
    }
}
