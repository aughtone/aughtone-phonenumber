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
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType as RefType
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber as RefPhoneNumber
import kotlin.test.Test
import kotlin.test.fail

/**
 * Type/validity-parity harness for AOPH-7 / #15. Uses the reference's own per-type example numbers
 * for every region (and non-geographical entity), and compares this port's [PhoneNumberUtil.getNumberType],
 * [PhoneNumberUtil.isValidNumber], [PhoneNumberUtil.isValidNumberForRegion],
 * [PhoneNumberUtil.getSupportedTypesForRegion] and [PhoneNumberUtil.getSupportedTypesForNonGeoEntity]
 * against the 9.0.39 reference. Faithful by construction (real numbers, upstream's own expectations).
 * JVM-only.
 */
class NumberTypeParityTest {

    private val ref = ReferenceUtil.getInstance()

    private fun ourType(pn: PhoneNumber) = PhoneNumberUtil.getNumberType(pn).name

    @Test
    fun getNumberTypeAndValidityMatchUpstream() {
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            for (refType in RefType.values()) {
                if (refType == RefType.UNKNOWN) continue
                val example = ref.getExampleNumberForType(region, refType) ?: continue
                val e164 = ref.format(example, RefFormat.E164)
                val refPn = try { ref.parse(e164, region) } catch (e: Exception) { continue }
                val ourPn = try {
                    PhoneNumberUtil.parse(e164, region, libphonenumberCompat = true)
                } catch (e: PhoneNumberUtil.NumberParseException) {
                    gaps += "[$region/$refType] parse $e164 threw ${e.errorType}"
                    continue
                }

                checks++
                val refTypeName = ref.getNumberType(refPn).name
                val ourTypeName = ourType(ourPn)
                if (refTypeName == ourTypeName) green++ else gaps += "[$region $e164] type ours=$ourTypeName ref=$refTypeName"

                checks++
                if (ref.isValidNumber(refPn) == PhoneNumberUtil.isValidNumber(ourPn)) green++
                else gaps += "[$region $e164] isValid ours=${PhoneNumberUtil.isValidNumber(ourPn)} ref=${ref.isValidNumber(refPn)}"

                checks++
                if (ref.isValidNumberForRegion(refPn, region) == PhoneNumberUtil.isValidNumberForRegion(ourPn, region)) green++
                else gaps += "[$region $e164] isValidForRegion mismatch"
            }
        }
        report("getNumberType/validity parity", green, checks, gaps)
    }

    @Test
    fun getSupportedTypesForRegionMatchesUpstream() {
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            checks++
            val refSet = ref.getSupportedTypesForRegion(region).map { it.name }.toSortedSet()
            val ourSet = PhoneNumberUtil.getSupportedTypesForRegion(region).map { it.name }.toSortedSet()
            if (refSet == ourSet) green++ else gaps += "[$region] ours=$ourSet ref=$refSet"
        }
        report("getSupportedTypesForRegion parity", green, checks, gaps)
    }

    @Test
    fun getSupportedTypesForNonGeoEntityMatchesUpstream() {
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (cc in ref.supportedGlobalNetworkCallingCodes.sorted()) {
            checks++
            val refSet = ref.getSupportedTypesForNonGeoEntity(cc).map { it.name }.toSortedSet()
            val ourSet = PhoneNumberUtil.getSupportedTypesForNonGeoEntity(cc).map { it.name }.toSortedSet()
            if (refSet == ourSet) green++ else gaps += "[+$cc] ours=$ourSet ref=$refSet"

            val example = ref.getExampleNumberForNonGeoEntity(cc) ?: continue
            val e164 = ref.format(example, RefFormat.E164)
            val refPn = try { ref.parse(e164, "ZZ") } catch (e: Exception) { continue }
            val ourPn = try {
                PhoneNumberUtil.parse(e164, "ZZ", libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }
            checks++
            if (ref.getNumberType(refPn).name == ourType(ourPn)) green++
            else gaps += "[+$cc $e164] type ours=${ourType(ourPn)} ref=${ref.getNumberType(refPn).name}"
        }
        report("getSupportedTypesForNonGeoEntity parity", green, checks, gaps)
    }

    /** Invalid / unknown numbers: getNumberType == UNKNOWN and isValidNumber == false, vs the reference. */
    @Test
    fun invalidNumbersMatchUpstream() {
        // (countryCode, nationalNumber) built directly in both libraries — no parsing.
        val cases = listOf(
            1 to "2530000",        // US, too short
            1 to "3000",           // US, invalid
            44 to "791234567",     // GB mobile, too short
            64 to "3316005",       // NZ, too short national
            49 to "1234",          // DE, too short
            999 to "123456789",    // unassigned calling code
            1 to "65025300001",    // US, too long
        )
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for ((cc, nsn) in cases) {
            val refPn = RefPhoneNumber().setCountryCode(cc).setNationalNumber(nsn.toLong())
            val ourPn = PhoneNumber(cc, nsn)
            checks++
            if (ref.getNumberType(refPn).name == ourType(ourPn)) green++
            else gaps += "[+$cc$nsn] type ours=${ourType(ourPn)} ref=${ref.getNumberType(refPn).name}"
            checks++
            if (ref.isValidNumber(refPn) == PhoneNumberUtil.isValidNumber(ourPn)) green++
            else gaps += "[+$cc$nsn] isValid ours=${PhoneNumberUtil.isValidNumber(ourPn)} ref=${ref.isValidNumber(refPn)}"
        }
        report("Invalid-number parity", green, checks, gaps)
    }

    private fun report(label: String, green: Int, checks: Int, gaps: List<String>) {
        val summary = "$label (compat mode): $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }
}
