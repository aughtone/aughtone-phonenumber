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

/** Non-ASCII decimal digits must normalize to the same byte-stable E.164 on every target. */
class UnicodeDigitsTest {

    private fun e164(number: String, region: String) =
        PhoneNumberUtil.parse(number, region).formatToE164()

    @Test fun fullwidthInternational() {
        // ＋１ ２０１ ５５５ ０１２３  (fullwidth plus + digits)
        assertEquals("+12015550123", e164("＋１ ２０１ ５５５ ０１２３", "US"))
    }

    @Test fun fullwidthNational() {
        // ０１２１ ２３４ ５６７８  (GB national form, fullwidth) -> strip trunk 0
        assertEquals("+441212345678", e164("０１２１ ２３４ ５６７８", "GB"))
    }

    @Test fun arabicIndic() {
        // Arabic-Indic digits for US NSN 2015550123, region US.
        assertEquals("+12015550123", e164("٢٠١٥٥٥٠١٢٣", "US"))
    }

    @Test fun easternArabicIndic() {
        // Eastern Arabic-Indic digits for US NSN 2015550123.
        assertEquals("+12015550123", e164("۲۰۱۵۵۵۰۱۲۳", "US"))
    }
}
