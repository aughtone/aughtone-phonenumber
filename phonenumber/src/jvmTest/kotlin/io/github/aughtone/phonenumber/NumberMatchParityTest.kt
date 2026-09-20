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
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber as RefPhoneNumber
import kotlin.test.Test
import kotlin.test.fail

/**
 * isNumberMatch-parity harness for AOPH-7 / #17. Compares this port's `isNumberMatch` (all three
 * overloads) to the 9.0.39 reference. String pairs are parsed by each library itself, so a mismatch
 * is purely an `isNumberMatch` difference; the direct-construction cases cover the italian-leading-zero
 * and ignored-field scenarios from upstream's own tests. Faithful by construction. JVM-only.
 */
class NumberMatchParityTest {

    private val ref = ReferenceUtil.getInstance()

    // Pairs drawn from upstream's testIsNumberMatch* methods, plus a few extra edge cases.
    private val stringPairs = listOf(
        // EXACT-ish (same number, different formatting / isub / country code presence)
        "+64 3 331 6005" to "+64 03 331 6005",
        "+643 331-6005" to "+64033316005",
        "+64 3 331-6005" to "+6433316005",
        "+64 3 331-6005" to "tel:+64-3-331-6005;isub=123",
        "+7 423 202-25-11" to "+7 4232022511",
        "+1 800 1234 5678" to "+1 800 1234 5678",
        // NSN / SHORT (one national, one international; suffixes)
        "03 331 6005" to "+64 3 331 6005",
        "331 6005" to "3 331 6005",
        "3 331 6005" to "03 331 6005",
        "+64 3 331-6005" to "3 331 6005",
        // Extensions
        "+64 3 331-6005 extn 1234" to "+6433316005#1234",
        "+64 3 331-6005 extn 1234" to "+64 3 331-6005 extn 1235",
        "+64 3 331-6005 extn 1234" to "+6433316005",
        "0800 DDA 005" to "0800 332 005",
        // NO_MATCH
        "03 331 6005" to "03 331 6006",
        "+800 1234 5678" to "+1 800 1234 5678",
        "+64 3 331-6005" to "+16433316005",
        "+64 3 331-6005" to "+6133316005",
        "+64 3 331-6005" to "+64 3 331-6006",
        // NOT_A_NUMBER
        "3" to "1 331 6005",
        "+64 3 331-6005" to "this is not a number",
        "not a number" to "also not",
    )

    @Test
    fun stringMatchesMatchUpstream() {
        val gaps = mutableListOf<String>()
        for ((a, b) in stringPairs) {
            val refM = ref.isNumberMatch(a, b).name
            val ourM = PhoneNumberUtil.isNumberMatch(a, b).name
            if (refM != ourM) gaps += "[\"$a\" vs \"$b\"] ours=$ourM ref=$refM"
        }
        report("String isNumberMatch parity", gaps)
    }

    @Test
    fun phoneNumberAndStringMatchesMatchUpstream() {
        val gaps = mutableListOf<String>()
        for ((a, b) in stringPairs) {
            val refFirst = try { ref.parse(a, "ZZ") } catch (e: Exception) { null } ?: continue
            val ourFirst = try {
                PhoneNumberUtil.parse(a, "ZZ", libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }
            val refM = ref.isNumberMatch(refFirst, b).name
            val ourM = PhoneNumberUtil.isNumberMatch(ourFirst, b).name
            if (refM != ourM) gaps += "[proto(\"$a\") vs \"$b\"] ours=$ourM ref=$refM"
        }
        report("PhoneNumber/String isNumberMatch parity", gaps)
    }

    @Test
    fun directConstructionMatchesMatchUpstream() {
        // (cc, coreDigits, italianLeadingZero, numberOfLeadingZeros, extension, carrier)
        data class N(
            val cc: Int, val core: String, val ilz: Boolean = false, val nlz: Int = 1,
            val ext: String? = null, val carrier: String? = null,
        )

        fun refOf(n: N): RefPhoneNumber {
            val p = RefPhoneNumber().setCountryCode(n.cc).setNationalNumber(n.core.toLong())
            if (n.ilz) p.setItalianLeadingZero(true)
            if (n.nlz != 1) p.setNumberOfLeadingZeros(n.nlz)
            n.ext?.let { p.setExtension(it) }
            n.carrier?.let { p.setPreferredDomesticCarrierCode(it) }
            return p
        }
        fun ourOf(n: N): PhoneNumber {
            val leading = if (n.ilz) "0".repeat(n.nlz) else ""
            return PhoneNumber(
                n.cc, leading + n.core,
                italianLeadingZero = n.ilz, numberOfLeadingZeros = n.nlz,
                extension = n.ext, preferredDomesticCarrierCode = n.carrier,
            )
        }

        val pairs = listOf(
            // EXACT: proto defaults / leading-zero-false variations
            N(64, "33316005") to N(64, "33316005"),
            N(64, "33316005") to N(64, "33316005", nlz = 1),
            N(64, "33316005", ilz = false) to N(64, "33316005", ilz = false),
            // Ignored fields (carrier code) → EXACT
            N(64, "33316005", carrier = "99") to N(64, "33316005", carrier = "01"),
            // SHORT_NSN: differing number of leading zeros
            N(64, "33316005", ilz = true, nlz = 1) to N(64, "33316005", ilz = true, nlz = 2),
            // SHORT_NSN: extension present vs absent
            N(64, "33316005", ext = "1234") to N(64, "33316005"),
            // NO_MATCH: different extensions
            N(64, "33316005", ext = "1234") to N(64, "33316005", ext = "1235"),
            // NSN_MATCH: one country code missing
            N(0, "33316005") to N(64, "33316005"),
            // NO_MATCH: different country codes
            N(1, "33316005") to N(64, "33316005"),
        )
        val gaps = mutableListOf<String>()
        for ((a, b) in pairs) {
            val refM = ref.isNumberMatch(refOf(a), refOf(b)).name
            val ourM = PhoneNumberUtil.isNumberMatch(ourOf(a), ourOf(b)).name
            if (refM != ourM) gaps += "[$a vs $b] ours=$ourM ref=$refM"
        }
        report("Direct-construction isNumberMatch parity", gaps)
    }

    private fun report(label: String, gaps: List<String>) {
        if (gaps.isNotEmpty()) fail("$label: ${gaps.size} gap(s):\n" + gaps.joinToString("\n"))
    }
}
