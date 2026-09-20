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
import kotlin.test.Test
import kotlin.test.fail

/**
 * parseAndKeepRawInput-parity harness for AOPH-7 / #18. For each region's example number it parses
 * several input forms (E.164, national) with `parseAndKeepRawInput` in both libraries and compares
 * `rawInput`, `countryCodeSource` and `preferredDomesticCarrierCode`. JVM-only.
 */
class RawInputParityTest {

    private val ref = ReferenceUtil.getInstance()

    @Test
    fun keepRawInputMatchesUpstream() {
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val inputs = listOf(
                ref.format(example, RefFormat.E164),
                ref.format(example, RefFormat.NATIONAL),
                ref.format(example, RefFormat.INTERNATIONAL),
            )
            for (input in inputs) {
                val refPn = try { ref.parseAndKeepRawInput(input, region) } catch (e: Exception) { null }
                val ourPn = try {
                    PhoneNumberUtil.parseAndKeepRawInput(input, region, libphonenumberCompat = true)
                } catch (e: PhoneNumberUtil.NumberParseException) { null }
                checks++
                if (refPn == null || ourPn == null) {
                    if ((refPn == null) != (ourPn == null)) {
                        gaps += "[$region \"$input\"] parse mismatch ref=${refPn != null} ours=${ourPn != null}"
                    } else {
                        green++
                    }
                    continue
                }
                val refTriple = listOf(refPn.rawInput, refPn.countryCodeSource.name, refPn.preferredDomesticCarrierCode)
                val ourTriple = listOf(ourPn.rawInput, ourPn.countryCodeSource.name, ourPn.preferredDomesticCarrierCode)
                if (refTriple == ourTriple) green++ else gaps += "[$region \"$input\"] ours=$ourTriple ref=$refTriple"
            }
        }
        val summary = "parseAndKeepRawInput parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }

    @Test
    fun formatInOriginalFormatMatchesUpstream() {
        val callingFrom = listOf("US", "GB", "DE")
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val inputs = listOf(
                ref.format(example, RefFormat.E164),
                ref.format(example, RefFormat.NATIONAL),
                ref.format(example, RefFormat.INTERNATIONAL),
            )
            for (input in inputs) {
                val refPn = try { ref.parseAndKeepRawInput(input, region) } catch (e: Exception) { continue }
                val ourPn = try {
                    PhoneNumberUtil.parseAndKeepRawInput(input, region, libphonenumberCompat = true)
                } catch (e: PhoneNumberUtil.NumberParseException) { continue }
                for (from in callingFrom) {
                    checks++
                    val refStr = ref.formatInOriginalFormat(refPn, from)
                    val ourStr = PhoneNumberUtil.formatInOriginalFormat(ourPn, from)
                    if (refStr == ourStr) green++ else gaps += "[$region \"$input\" from $from] ours=\"$ourStr\" ref=\"$refStr\""
                }
            }
        }
        val summary = "formatInOriginalFormat parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }

    @Test
    fun formatOutOfCountryKeepingAlphaCharsMatchesUpstream() {
        val callingFrom = listOf("US", "GB", "DE", "AU", "FR")
        val gaps = mutableListOf<String>()
        var checks = 0
        var green = 0

        // Example-number forms across all regions (exercises the non-alpha branches).
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val inputs = listOf(
                ref.format(example, RefFormat.E164),
                ref.format(example, RefFormat.NATIONAL),
                ref.format(example, RefFormat.INTERNATIONAL),
            )
            for (input in inputs) {
                val refPn = try { ref.parseAndKeepRawInput(input, region) } catch (e: Exception) { continue }
                val ourPn = try {
                    PhoneNumberUtil.parseAndKeepRawInput(input, region, libphonenumberCompat = true)
                } catch (e: PhoneNumberUtil.NumberParseException) { continue }
                for (from in callingFrom) {
                    checks++
                    val refStr = ref.formatOutOfCountryKeepingAlphaChars(refPn, from)
                    val ourStr = PhoneNumberUtil.formatOutOfCountryKeepingAlphaChars(ourPn, from)
                    if (refStr == ourStr) green++ else gaps += "[$region \"$input\" from $from] ours=\"$ourStr\" ref=\"$refStr\""
                }
            }
        }

        // Alpha inputs (the point of this method): keep the letters.
        val alphaCases = listOf(
            "1800 six-flags" to "US",
            "1-800-SIX-FLAG" to "US",
            "(0800) DDA 005" to "NZ",
        )
        for ((input, region) in alphaCases) {
            val refPn = try { ref.parseAndKeepRawInput(input, region) } catch (e: Exception) { continue }
            val ourPn = try {
                PhoneNumberUtil.parseAndKeepRawInput(input, region, libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }
            for (from in callingFrom) {
                checks++
                val refStr = ref.formatOutOfCountryKeepingAlphaChars(refPn, from)
                val ourStr = PhoneNumberUtil.formatOutOfCountryKeepingAlphaChars(ourPn, from)
                if (refStr == ourStr) green++ else gaps += "[\"$input\" from $from] ours=\"$ourStr\" ref=\"$refStr\""
            }
        }

        val summary = "formatOutOfCountryKeepingAlphaChars parity: $green/$checks match; ${gaps.size} gap(s)."
        println(summary)
        gaps.take(60).forEach { println("  GAP $it") }
        if (gaps.isNotEmpty()) fail("$summary\n" + gaps.take(60).joinToString("\n"))
    }
}
