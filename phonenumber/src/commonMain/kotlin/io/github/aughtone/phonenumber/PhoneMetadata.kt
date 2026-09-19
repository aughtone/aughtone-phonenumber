/*
 * Copyright (C) 2010 The Libphonenumber Authors
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
 *
 * Modifications: ported from the original Java implementation to Kotlin
 * Multiplatform. Structure and behaviour follow the original; the implementation
 * was rewritten in Kotlin, and reduced to the subset the E.164 normalizer needs.
 */
package io.github.aughtone.phonenumber

/**
 * The subset of libphonenumber's per-region metadata that this port's parser,
 * E.164 formatter, and length checks need. The full model carries more still
 * (number-format templates); those are added as the port grows.
 *
 * Instances are produced by the metadata-generator from the pinned metadata XML
 * (see [METADATA_VERSION]) and embedded as Kotlin — no runtime resource loading.
 */
internal data class PhoneMetadata(
    /** ISO region code, e.g. "US"; or a non-geographical calling code id like "001". */
    val id: String,
    /** Country calling code, e.g. 1 for NANP, 44 for GB. */
    val countryCode: Int,
    /** True for the primary region of a shared calling code (e.g. US for +1). */
    val mainCountryForCode: Boolean,
    /** IDD prefix used to dial out of the region, e.g. "011" (US), "00" (GB). */
    val internationalPrefix: String?,
    /** National (trunk) prefix, e.g. "1" (US), "0" (GB). */
    val nationalPrefix: String?,
    /** Regex used to strip the national prefix (and carrier code) during parsing. */
    val nationalPrefixForParsing: String?,
    /** Replacement applied after [nationalPrefixForParsing] matches (may reference groups). */
    val nationalPrefixTransformRule: String?,
    /** General national-number validity pattern (whitespace already stripped). */
    val generalNationalNumberPattern: String,
    /** Territory-level leading-digits pattern used to resolve shared calling codes (or null). */
    val leadingDigits: String?,
    /** Per-number-type national-number patterns (e.g. "fixedLine", "mobile"), whitespace stripped. */
    val typePatterns: Map<String, String>,
    /**
     * General possible national-number lengths, sorted ascending — the union of every type's
     * lengths (libphonenumber's build computes the general desc this way). `[-1]` means no
     * numbers exist for the region, which never matches a real length. Drives isPossibleNumber.
     */
    val possibleLengths: List<Int> = emptyList(),
    /** General local-only lengths, sorted ascending, with any that are also national removed. */
    val possibleLengthsLocalOnly: List<Int> = emptyList(),
    /** Per-type national possible lengths, only where the XML states them explicitly (else fall back to general). */
    val typePossibleLengths: Map<String, List<Int>> = emptyMap(),
    /** Per-type local-only possible lengths, only where the XML states them explicitly. */
    val typePossibleLengthsLocalOnly: Map<String, List<Int>> = emptyMap(),
)
