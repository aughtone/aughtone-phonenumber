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
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber as RefNumber
import kotlin.test.Test
import kotlin.test.fail

/**
 * #16 parity: our possible-length classification and truncation vs the reference. Expected values
 * come from the reference itself (faithful by construction). Inputs use no leading zeros, so a
 * reference number built from the long NSN keeps the same length as ours.
 */
class PossibleNumberParityTest {

    private val ref = ReferenceUtil.getInstance()
    private fun refNum(cc: Int, nsn: String) = RefNumber().setCountryCode(cc).setNationalNumber(nsn.toLong())
    private fun ours(cc: Int, nsn: String) = PhoneNumber(cc, nsn)

    private val lengthCases = listOf(
        1 to "6502530000", 1 to "2530000", 1 to "650253000000", 1 to "253000", 1 to "0",
        44 to "2070313000", 44 to "20703130000", 44 to "7031",
        64 to "33316005", 64 to "3331600", 64 to "333160055",
        49 to "30123456", 49 to "3012345678",
        52 to "4499780001", 52 to "13312345678",
        999 to "12345",
    )

    @Test
    fun isPossibleNumberWithReasonMatchesReference() {
        val gaps = mutableListOf<String>()
        for ((cc, nsn) in lengthCases) {
            val refR = ref.isPossibleNumberWithReason(refNum(cc, nsn)).name
            val ourR = PhoneNumberUtil.isPossibleNumberWithReason(ours(cc, nsn)).name
            if (refR != ourR) gaps += "+$cc $nsn: ours=$ourR ref=$refR"
        }
        if (gaps.isNotEmpty()) fail("${gaps.size} possible-length disagreement(s):\n" + gaps.joinToString("\n"))
    }

    private val typeCases = listOf(
        Triple(1, "6502530000", PhoneNumberUtil.PhoneNumberType.MOBILE),
        Triple(1, "6502530000", PhoneNumberUtil.PhoneNumberType.FIXED_LINE),
        Triple(1, "8002345678", PhoneNumberUtil.PhoneNumberType.TOLL_FREE),
        Triple(1, "8002345678", PhoneNumberUtil.PhoneNumberType.MOBILE),
        Triple(64, "33316005", PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE),
        Triple(44, "7400000000", PhoneNumberUtil.PhoneNumberType.MOBILE),
        Triple(44, "2070313000", PhoneNumberUtil.PhoneNumberType.FIXED_LINE),
    )

    @Test
    fun isPossibleNumberForTypeWithReasonMatchesReference() {
        val gaps = mutableListOf<String>()
        for ((cc, nsn, type) in typeCases) {
            val refType = ReferenceUtil.PhoneNumberType.valueOf(type.name)
            val refR = ref.isPossibleNumberForTypeWithReason(refNum(cc, nsn), refType).name
            val ourR = PhoneNumberUtil.isPossibleNumberForTypeWithReason(ours(cc, nsn), type).name
            if (refR != ourR) gaps += "+$cc $nsn [$type]: ours=$ourR ref=$refR"
        }
        if (gaps.isNotEmpty()) fail("${gaps.size} for-type disagreement(s):\n" + gaps.joinToString("\n"))
    }

    @Test
    fun truncateTooLongNumberMatchesReference() {
        val cases = listOf(1 to "650253000000", 1 to "6502530000", 1 to "12", 44 to "20703130000")
        val gaps = mutableListOf<String>()
        for ((cc, nsn) in cases) {
            val rn = refNum(cc, nsn)
            val refNsn = if (ref.truncateTooLongNumber(rn)) ref.getNationalSignificantNumber(rn) else null
            val ourNsn = PhoneNumberUtil.truncateTooLongNumber(ours(cc, nsn))?.nationalNumber
            if (refNsn != ourNsn) gaps += "+$cc $nsn: ours=$ourNsn ref=$refNsn"
        }
        if (gaps.isNotEmpty()) fail("${gaps.size} truncate disagreement(s):\n" + gaps.joinToString("\n"))
    }
}
