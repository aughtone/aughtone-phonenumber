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

import io.github.aughtone.phonenumber.PhoneNumberUtil.MatchType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #17 isNumberMatch, cross-target with deterministic expected values confirmed against the 9.0.39
 * reference (see `NumberMatchParityTest`). Runs the matcher on every target for byte-stable behaviour.
 */
class NumberMatchTest {

    private fun match(a: String, b: String) = PhoneNumberUtil.isNumberMatch(a, b)

    @Test fun exactMatchIgnoresFormatting() {
        assertEquals(MatchType.EXACT_MATCH, match("+64 3 331 6005", "+64 03 331 6005"))
    }

    @Test fun differentCountryCodeIsNoMatch() {
        assertEquals(MatchType.NO_MATCH, match("+64 3 331-6005", "+16433316005"))
    }

    @Test fun nationalVsInternationalIsNsnMatch() {
        assertEquals(MatchType.NSN_MATCH, match("03 331 6005", "+64 3 331 6005"))
    }

    @Test fun suffixIsShortNsnMatch() {
        assertEquals(MatchType.SHORT_NSN_MATCH, match("331 6005", "3 331 6005"))
    }

    @Test fun nonNumberIsNotANumber() {
        assertEquals(MatchType.NOT_A_NUMBER, match("+64 3 331-6005", "this is not a number"))
    }
}
