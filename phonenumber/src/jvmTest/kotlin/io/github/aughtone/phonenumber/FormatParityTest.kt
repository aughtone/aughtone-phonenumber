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
import com.google.i18n.phonenumbers.Phonemetadata.NumberFormat as RefNumberFormat
import kotlin.test.Test
import kotlin.test.fail

/**
 * Formatting-parity harness for AOPH-7 / #14. For every region the reference supports it takes the
 * reference's own example number, and compares this port's `format(...)` in each
 * [PhoneNumberUtil.PhoneNumberFormat] against the 9.0.39 reference. The same E.164 is parsed by both
 * libraries first, so a mismatch is purely a formatting difference, not a parse one.
 *
 * Faithful by construction: the expected value is whatever upstream produces for a real, valid number
 * of each region — not a transcribed constant. This is the parity guard behind #14's `testFormat*`
 * coverage. JVM-only: parity can only be checked where the reference exists.
 */
class FormatParityTest {

    private val ref = ReferenceUtil.getInstance()

    private val formats = listOf(
        PhoneNumberUtil.PhoneNumberFormat.E164 to RefFormat.E164,
        PhoneNumberUtil.PhoneNumberFormat.NATIONAL to RefFormat.NATIONAL,
        PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL to RefFormat.INTERNATIONAL,
        PhoneNumberUtil.PhoneNumberFormat.RFC3966 to RefFormat.RFC3966,
    )

    @Test
    fun formatMatchesUpstreamForExampleNumbers() {
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val e164 = ref.format(example, RefFormat.E164)

            val refPn = try {
                ref.parse(e164, region)
            } catch (e: Exception) {
                continue
            }
            val ourPn = try {
                PhoneNumberUtil.parse(e164, region, libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) {
                gaps += "[$region] parse of $e164 threw ${e.errorType} (reference parsed it)"
                continue
            }

            for ((ours, refFmt) in formats) {
                checks++
                val refStr = ref.format(refPn, refFmt)
                val ourStr = PhoneNumberUtil.format(ourPn, ours)
                if (refStr == ourStr) green++ else gaps += "[$region] $ours: ours=\"$ourStr\"  ref=\"$refStr\""
            }
        }
        val summary = "Format parity (compat mode): $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }

    /** Out-of-country dialling parity: each region's example number dialled from several regions. */
    @Test
    fun formatOutOfCountryMatchesUpstream() {
        val callingFrom = listOf("US", "GB", "DE", "JP", "AU", "BR", "FR", "IT", "RU", "BY")
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val e164 = ref.format(example, RefFormat.E164)
            val refPn = try { ref.parse(e164, region) } catch (e: Exception) { continue }
            val ourPn = try {
                PhoneNumberUtil.parse(e164, region, libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }
            for (from in callingFrom) {
                checks++
                val refStr = ref.formatOutOfCountryCallingNumber(refPn, from)
                val ourStr = PhoneNumberUtil.formatOutOfCountryCallingNumber(ourPn, from)
                if (refStr == ourStr) green++ else gaps += "[$region from $from] ours=\"$ourStr\"  ref=\"$refStr\""
            }
        }
        val summary = "Out-of-country parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }

    /** Mobile-dialing parity: each region's example number, dialled from several regions, both modes. */
    @Test
    fun formatForMobileDialingMatchesUpstream() {
        val callingFrom = listOf("US", "GB", "DE", "MX", "CL", "UZ", "BR", "CN", "AR", "ZZ")
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val e164 = ref.format(example, RefFormat.E164)
            val refPn = try { ref.parse(e164, region) } catch (e: Exception) { continue }
            val ourPn = try {
                PhoneNumberUtil.parse(e164, region, libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }
            for (from in callingFrom) {
                for (fmt in listOf(true, false)) {
                    checks++
                    val refStr = ref.formatNumberForMobileDialing(refPn, from, fmt)
                    val ourStr = PhoneNumberUtil.formatNumberForMobileDialing(ourPn, from, fmt)
                    if (refStr == ourStr) green++ else gaps += "[$region from $from fmt=$fmt] ours=\"$ourStr\"  ref=\"$refStr\""
                }
            }
        }
        val summary = "Mobile-dialing parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }

    /** Carrier-code formatting parity: each region's example number with a fixed carrier code. */
    @Test
    fun formatWithCarrierCodeMatchesUpstream() {
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val e164 = ref.format(example, RefFormat.E164)
            val refPn = try { ref.parse(e164, region) } catch (e: Exception) { continue }
            val ourPn = try {
                PhoneNumberUtil.parse(e164, region, libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }
            checks++
            val refStr = ref.formatNationalNumberWithCarrierCode(refPn, "15")
            val ourStr = PhoneNumberUtil.formatNationalNumberWithCarrierCode(ourPn, "15")
            if (refStr == ourStr) green++ else gaps += "[$region] ours=\"$ourStr\"  ref=\"$refStr\""
        }
        val summary = "Carrier-code parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }

    /** Extension formatting parity: a few inputs carrying extensions, across the display formats. */
    @Test
    fun formatWithExtensionMatchesUpstream() {
        val cases = listOf(
            "+442034567890x456" to "GB",
            "(800) 901-3355 x 7246433" to "US",
            "+6433316005 ext 3456" to "NZ",
            "+493012345678-12" to "DE",
        )
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for ((input, region) in cases) {
            val refPn = try { ref.parse(input, region) } catch (e: Exception) { continue }
            val ourPn = PhoneNumberUtil.parse(input, region, libphonenumberCompat = true)
            for ((ours, refFmt) in formats) {
                checks++
                val refStr = ref.format(refPn, refFmt)
                val ourStr = PhoneNumberUtil.format(ourPn, ours)
                if (refStr == ourStr) green++ else gaps += "[$input $ours] ours=\"$ourStr\"  ref=\"$refStr\""
            }
        }
        val summary = "Extension-format parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.joinToString("\n"))
    }

    /** By-pattern parity: a caller-supplied format applied to a US number, vs the reference. */
    @Test
    fun formatByPatternMatchesUpstream() {
        val ourPn = PhoneNumberUtil.parse("+16502530000", "US", libphonenumberCompat = true)
        val refPn = ref.parse("+16502530000", "US")

        val ours = listOf(
            NumberFormat(pattern = "(\\d{3})(\\d{3})(\\d{4})", format = "\$1.\$2.\$3"),
        )
        val theirBuilder = RefNumberFormat.newBuilder()
        theirBuilder.setPattern("(\\d{3})(\\d{3})(\\d{4})").setFormat("\$1.\$2.\$3")
        val theirs = listOf(theirBuilder.build())
        val gaps = mutableListOf<String>()
        for ((oursFmt, refFmt) in formats) {
            if (oursFmt == PhoneNumberUtil.PhoneNumberFormat.E164) continue // E164 ignores custom patterns
            val ourStr = PhoneNumberUtil.formatByPattern(ourPn, oursFmt, ours)
            val refStr = ref.formatByPattern(refPn, refFmt, theirs)
            if (ourStr != refStr) gaps += "[$oursFmt] ours=\"$ourStr\"  ref=\"$refStr\""
        }
        if (gaps.isNotEmpty()) fail("By-pattern parity gaps:\n" + gaps.joinToString("\n"))
    }

    /** Edge cases: out-of-country from an invalid region, and the preferred-carrier fallback. */
    @Test
    fun formatEdgeCasesMatchUpstream() {
        val gaps = mutableListOf<String>()

        // Out-of-country from an unknown region falls back to INTERNATIONAL.
        val us = PhoneNumberUtil.parse("+16502530000", "US", libphonenumberCompat = true)
        val refUs = ref.parse("+16502530000", "US")
        run {
            val ourStr = PhoneNumberUtil.formatOutOfCountryCallingNumber(us, "ZZ")
            val refStr = ref.formatOutOfCountryCallingNumber(refUs, "ZZ")
            if (ourStr != refStr) gaps += "[outOfCountry invalid region] ours=\"$ourStr\"  ref=\"$refStr\""
        }

        // Preferred carrier code with a fallback (no preferred set on the number → fallback used).
        run {
            val ourStr = PhoneNumberUtil.formatNationalNumberWithPreferredCarrierCode(us, "15")
            val refStr = ref.formatNationalNumberWithPreferredCarrierCode(refUs, "15")
            if (ourStr != refStr) gaps += "[preferredCarrier fallback] ours=\"$ourStr\"  ref=\"$refStr\""
        }

        if (gaps.isNotEmpty()) fail("Format edge-case gaps:\n" + gaps.joinToString("\n"))
    }
}
