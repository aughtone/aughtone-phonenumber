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

/**
 * #21 — `isSupportedRegion`, the O(1) convenience for geographic-region validity. It must agree
 * exactly with membership in [PhoneNumberUtil.getSupportedRegions] and reject the non-geo entity
 * ("001") and unknown codes.
 */
class IsSupportedRegionTest {

    @Test fun agreesWithGetSupportedRegionsForEveryRegion() {
        // Every supported region reports true, and — the other direction — isSupportedRegion is true
        // for exactly the members of getSupportedRegions() and nothing else the set does not contain.
        val supported = PhoneNumberUtil.getSupportedRegions()
        assertTrue(supported.isNotEmpty(), "metadata should expose supported regions")
        for (region in supported) {
            assertTrue(PhoneNumberUtil.isSupportedRegion(region), "expected supported: $region")
        }
    }

    @Test fun knownRegionsAreSupported() {
        for (region in listOf("US", "GB", "DE", "CA", "NZ", "AT", "CH")) {
            assertTrue(PhoneNumberUtil.isSupportedRegion(region), region)
        }
    }

    @Test fun nonGeoEntityAndUnknownCodesAreNotSupported() {
        // "001" is the non-geographical entity (not a geographic region); the rest are unknown/garbage.
        for (bad in listOf("001", "ZZ", "", "  ", "USA", "u", "12", "us", "gb", "😀")) {
            assertFalse(PhoneNumberUtil.isSupportedRegion(bad), "expected unsupported: '$bad'")
        }
        // Matching is case-sensitive: the lower-case forms above are not members of getSupportedRegions().
        assertFalse("us" in PhoneNumberUtil.getSupportedRegions())
    }

    @Test fun matchesSetMembershipExactly() {
        val supported = PhoneNumberUtil.getSupportedRegions()
        for (candidate in listOf("US", "GB", "DE", "001", "ZZ", "", "XX", "us")) {
            assertEquals(candidate in supported, PhoneNumberUtil.isSupportedRegion(candidate), candidate)
        }
    }
}
