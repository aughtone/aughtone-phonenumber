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
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import kotlin.test.Test
import kotlin.test.fail

/**
 * Ground-truth check against the real libphonenumber Java (pinned to the same
 * metadata epoch). For every region's canonical example number, formats it three
 * ways with the reference (national, international, E.164), feeds each dialing
 * string to this port, and asserts our E.164 equals the reference's E.164.
 *
 * JVM-only (the reference is a Java library). Correctness here plus the
 * cross-target ConformanceTest together give: correct AND byte-stable everywhere.
 */
class GroundTruthTest {

    @Test
    fun e164MatchesReferenceForEveryRegionExample() {
        val ref = ReferenceUtil.getInstance()
        val failures = mutableListOf<String>()
        var tested = 0
        var regions = 0

        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            regions++
            val refE164 = ref.format(example, PhoneNumberFormat.E164)
            for (fmt in listOf(
                PhoneNumberFormat.NATIONAL,
                PhoneNumberFormat.INTERNATIONAL,
                PhoneNumberFormat.E164,
            )) {
                val dialed = ref.format(example, fmt)
                tested++
                val mine = runCatching { PhoneNumberUtil.parse(dialed, region).formatToE164() }
                    .getOrElse { "EXCEPTION(${it::class.simpleName}: ${it.message})" }
                if (mine != refE164) {
                    failures += "$region [$fmt] \"$dialed\": mine=$mine ref=$refE164"
                }
                // isValid must agree with the reference (examples are valid).
                val myValid = runCatching { PhoneNumberUtil.isValid(dialed, region) }.getOrDefault(false)
                if (!myValid) failures += "$region [$fmt] \"$dialed\": isValid=false, ref valid"
            }
        }

        val version = ReferenceUtil::class.java.`package`?.implementationVersion ?: "?"
        println("Ground-truth: $tested dialings across $regions regions vs libphonenumber $version; ${failures.size} mismatch(es).")
        if (failures.isNotEmpty()) {
            fail("${failures.size} of $tested ground-truth mismatch(es):\n" + failures.take(80).joinToString("\n"))
        }
    }

    @Test
    fun isValidAgreesWithReferenceOnLengthVariants() {
        val ref = ReferenceUtil.getInstance()
        val failures = mutableListOf<String>()
        var tested = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val cc = example.countryCode
            val nsn = ref.getNationalSignificantNumber(example) // preserves leading zeros
            val variants = listOf(nsn + "0", if (nsn.length > 3) nsn.dropLast(1) else nsn + "00")
            for (variant in variants) {
                val e164 = "+$cc$variant"
                val refValid = runCatching { ref.isValidNumber(ref.parse(e164, region)) }.getOrDefault(false)
                val myValid = runCatching { PhoneNumberUtil.isValid(e164, region) }.getOrDefault(false)
                tested++
                if (myValid != refValid) failures += "$region \"$e164\": mine=$myValid ref=$refValid"
            }
        }
        println("isValid length-variant agreement: $tested checks; ${failures.size} disagreement(s).")
        if (failures.isNotEmpty()) {
            fail("${failures.size} of $tested isValid disagreements:\n" + failures.take(80).joinToString("\n"))
        }
    }
}
