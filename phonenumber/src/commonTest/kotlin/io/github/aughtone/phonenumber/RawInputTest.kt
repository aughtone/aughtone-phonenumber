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

/**
 * #18 parseAndKeepRawInput, cross-target with deterministic expected values confirmed against the
 * 9.0.39 reference (see `RawInputParityTest`). The recorded fields never change the E.164 form.
 */
class RawInputTest {

    @Test fun recordsRawInputAndSource() {
        val plus = PhoneNumberUtil.parseAndKeepRawInput("+16502530000", "US")
        assertEquals("+16502530000", plus.rawInput)
        assertEquals(CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN, plus.countryCodeSource)

        assertEquals(
            CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN,
            PhoneNumberUtil.parseAndKeepRawInput("1 650 253 0000", "US").countryCodeSource,
        )
        assertEquals(
            CountryCodeSource.FROM_NUMBER_WITH_IDD,
            PhoneNumberUtil.parseAndKeepRawInput("011 64 3 331 6005", "US").countryCodeSource,
        )
        assertEquals(
            CountryCodeSource.FROM_DEFAULT_COUNTRY,
            PhoneNumberUtil.parseAndKeepRawInput("(650) 253-0000", "US").countryCodeSource,
        )
    }

    @Test fun rawInputFieldsDoNotAffectE164() {
        val kept = PhoneNumberUtil.parseAndKeepRawInput("+1 (650) 253-0000", "US")
        val plain = PhoneNumberUtil.parse("+1 (650) 253-0000", "US")
        assertEquals("+16502530000", kept.formatToE164())
        assertEquals(plain.formatToE164(), kept.formatToE164())
    }
}
