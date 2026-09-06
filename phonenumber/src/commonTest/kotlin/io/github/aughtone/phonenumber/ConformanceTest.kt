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
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Byte-stability conformance corpus. Runs every calling code's main-region example
 * number (generated from the pinned metadata) through the parser on whatever target
 * is executing. Because the identical corpus and identical expected values run on
 * JVM, JS, and wasmJs, matching here on every target proves cross-target byte
 * stability — in particular that each region's general-pattern regex behaves the
 * same across the three regex engines (the portability long-pole).
 */
class ConformanceTest {

    @Test
    fun corpusIsNonTrivial() {
        // Guard against an empty/short generated corpus silently passing.
        assertTrue(METADATA_EXAMPLES.size > 200, "corpus too small: ${METADATA_EXAMPLES.size}")
    }

    @Test
    fun everyExampleParsesToStableE164AndValidates() {
        val failures = mutableListOf<String>()
        for (ex in METADATA_EXAMPLES) {
            val e164 = "+${ex.countryCode}${ex.nationalNumber}"

            // International form must round-trip to itself, region-independently.
            val fromRegion = PhoneNumberUtil.parse(e164, ex.region).formatToE164()
            if (fromRegion != e164) failures += "${ex.region}: parse($e164, ${ex.region}) -> $fromRegion"

            val fromUs = PhoneNumberUtil.parse(e164, "US").formatToE164()
            if (fromUs != e164) failures += "${ex.region}: parse($e164, US) -> $fromUs"

            // Exercises the region's general-number pattern regex on this engine.
            if (!PhoneNumberUtil.isValid(e164, ex.region)) failures += "${ex.region}: !isValid($e164)"
        }
        if (failures.isNotEmpty()) {
            val shown = failures.take(40).joinToString("\n")
            fail("${failures.size} conformance failure(s) of ${METADATA_EXAMPLES.size} examples:\n$shown")
        }
    }

    @Test
    fun everyExampleDialedNationallyParsesToStableE164() {
        // Dials each example in national form (national prefix + NSN) and expects
        // the same E.164 — this exercises the national-prefix / transform-rule
        // strip path on whatever target is executing.
        val failures = mutableListOf<String>()
        var tested = 0
        for (ex in METADATA_EXAMPLES) {
            val meta = GENERATED_METADATA[ex.region] ?: continue
            val np = meta.nationalPrefix
            if (np.isNullOrEmpty()) continue // no trunk prefix to prepend
            tested++
            val national = np + ex.nationalNumber
            val e164 = "+${ex.countryCode}${ex.nationalNumber}"
            val got = PhoneNumberUtil.parse(national, ex.region).formatToE164()
            if (got != e164) failures += "${ex.region}: parse(NATIONAL $national) -> $got (want $e164)"
        }
        if (failures.isNotEmpty()) {
            val shown = failures.take(40).joinToString("\n")
            fail("${failures.size} national-form failure(s) of $tested tested:\n$shown")
        }
    }
}
