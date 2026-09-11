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

/**
 * The Unicode version the embedded decimal-digit table is frozen at. Exposed —
 * like [METADATA_VERSION] — so a byte-stability-sensitive consumer can pin it and
 * reuse [decimalDigitValue] / [normalizeDigitsOnly] under the same version rather
 * than shipping its own table.
 */
public const val DIGIT_UNICODE_VERSION: String = "17.0.0"

/*
 * Frozen table of the 77 Unicode 17.0.0 decimal-digit (General_Category=Nd) block
 * starts (37 in the BMP, 40 supplementary), each the "0" of a run of 0-9. Facts,
 * not third-party expression, so no separate licence attaches. Regenerate with:
 *   curl -s https://www.unicode.org/Public/17.0.0/ucd/UnicodeData.txt |
 *   awk -F';' '$3=="Nd" && $6=="0" { print $1 }'
 * A frozen table is deterministic on every target; the platform's own \p{Nd} /
 * Character.digit follow the runtime's Unicode version and are not byte-stable.
 */
private val ND_BLOCK_STARTS = intArrayOf(
    0x0030, 0x0660, 0x06F0, 0x07C0, 0x0966, 0x09E6, 0x0A66, 0x0AE6,
    0x0B66, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66, 0x0DE6, 0x0E50, 0x0ED0,
    0x0F20, 0x1040, 0x1090, 0x17E0, 0x1810, 0x1946, 0x19D0, 0x1A80,
    0x1A90, 0x1B50, 0x1BB0, 0x1C40, 0x1C50, 0xA620, 0xA8D0, 0xA900,
    0xA9D0, 0xA9F0, 0xAA50, 0xABF0, 0xFF10, 0x104A0, 0x10D30, 0x10D40,
    0x11066, 0x110F0, 0x11136, 0x111D0, 0x112F0, 0x11450, 0x114D0, 0x11650,
    0x116C0, 0x116D0, 0x116DA, 0x11730, 0x118E0, 0x11950, 0x11BF0, 0x11C50,
    0x11D50, 0x11DA0, 0x11DE0, 0x11F50, 0x16130, 0x16A60, 0x16AC0, 0x16B50,
    0x16D70, 0x1CCF0, 0x1D7CE, 0x1D7D8, 0x1D7E2, 0x1D7EC, 0x1D7F6, 0x1E140,
    0x1E2F0, 0x1E4F0, 0x1E5F1, 0x1E950, 0x1FBF0,
)

/**
 * The decimal value 0..9 of [codePoint] if it is a Unicode decimal digit (Nd) in
 * the frozen [DIGIT_UNICODE_VERSION] table, else null. Covers all 77 blocks,
 * including supplementary (> U+FFFF) code points. Deterministic on every target.
 */
public fun decimalDigitValue(codePoint: Int): Int? {
    val a = ND_BLOCK_STARTS
    var lo = 0
    var hi = a.size - 1
    var start = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (a[mid] <= codePoint) {
            start = a[mid]
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (start < 0) return null
    val d = codePoint - start
    return if (d in 0..9) d else null
}

/**
 * Convert every Unicode decimal digit in [input] to its ASCII 0-9 form and drop
 * everything else. Mirrors libphonenumber's `normalizeDigitsOnly`, but by default
 * recognises all Unicode [DIGIT_UNICODE_VERSION] decimal digits, including
 * supplementary (> U+FFFF) ones.
 *
 * @param libphonenumberCompat when true, matches upstream exactly — only BMP
 *   (<= U+FFFF) decimal digits are recognised and supplementary digits are
 *   silently stripped, because upstream reads one UTF-16 char at a time.
 */
public fun normalizeDigitsOnly(input: CharSequence, libphonenumberCompat: Boolean = false): String {
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        val cp: Int
        if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) {
            cp = 0x10000 + ((c.code - 0xD800) shl 10) + (input[i + 1].code - 0xDC00)
            i += 2
        } else {
            cp = c.code
            i += 1
        }
        if (libphonenumberCompat && cp > 0xFFFF) continue // upstream strips supplementary digits
        val d = decimalDigitValue(cp) ?: continue
        sb.append('0' + d)
    }
    return sb.toString()
}
