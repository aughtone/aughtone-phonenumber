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
package io.github.aughtone.phonenumber.tools

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the pinned libphonenumber metadata XML (bundled as a resource) and emits:
 *  - `GeneratedMetadata.kt` into the library's commonMain (embedded metadata), and
 *  - `GeneratedExamples.kt` into commonTest (a byte-stability conformance corpus).
 *
 * The metadata is compiled into the binary on every target with no runtime
 * resource loading. This tool is original to this repository (build tooling, not
 * derived from Google's Java sources), so it carries only the Aught One copyright.
 *
 * Usage: Generator <commonMainOutDir> <commonTestOutDir>
 */
private const val RESOURCE = "/metadata/PhoneNumberMetadata.xml"

// Number-type descriptors carried for validation (order is not significant here;
// PhoneNumberUtil applies libphonenumber's matching order).
private val TYPES = listOf(
    "fixedLine", "mobile", "tollFree", "premiumRate", "sharedCost",
    "personalNumber", "voip", "pager", "uan", "voicemail",
)

private data class Nf(
    val pattern: String,
    val format: String,
    val leadingDigits: List<String>,
    val nationalPrefixFormattingRule: String?,
    val nationalPrefixOptionalWhenFormatting: Boolean,
    val domesticCarrierCodeFormattingRule: String?,
)

private data class Region(
    val id: String,
    val countryCode: Int,
    val mainCountryForCode: Boolean,
    val internationalPrefix: String?,
    val nationalPrefix: String?,
    val nationalPrefixForParsing: String?,
    val nationalPrefixTransformRule: String?,
    val generalPattern: String,
    val leadingDigits: String?,
    val typePatterns: Map<String, String>,
    val possibleLengths: List<Int>,
    val possibleLengthsLocalOnly: List<Int>,
    val typePossibleLengths: Map<String, List<Int>>,
    val typePossibleLengthsLocalOnly: Map<String, List<Int>>,
    val numberFormats: List<Nf>,
    val intlNumberFormats: List<Nf>,
    val preferredExtnPrefix: String?,
    val preferredInternationalPrefix: String?,
    val noInternationalDialling: String?,
    val mobileNumberPortableRegion: Boolean,
    val typeExampleNumbers: Map<String, String>,
    val exampleNumber: String?,
)

fun main(args: Array<String>) {
    require(args.size >= 2) { "Usage: Generator <commonMainOutDir> <commonTestOutDir>" }
    val mainDir = File(args[0]).apply { mkdirs() }
    val testDir = File(args[1]).apply { mkdirs() }

    val stream = object {}.javaClass.getResourceAsStream(RESOURCE)
        ?: error("Metadata resource not found on classpath: $RESOURCE")

    val doc = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(stream)

    val territories = doc.getElementsByTagName("territory")
    val regions = mutableListOf<Region>()
    var skipped = 0
    for (i in 0 until territories.length) {
        val t = territories.item(i) as Element
        val id = t.getAttribute("id")
        val ccStr = t.getAttribute("countryCode")
        val generalPattern = typePattern(t, "generalDesc")
        if (id.isEmpty() || ccStr.isEmpty() || generalPattern.isNullOrEmpty()) {
            skipped++
            continue
        }
        val typePatterns = LinkedHashMap<String, String>()
        for (type in TYPES) typePattern(t, type)?.let { typePatterns[type] = it }

        val typeExampleNumbers = LinkedHashMap<String, String>()
        for (type in TYPES) {
            firstChild(t, type)?.let { firstChild(it, "exampleNumber")?.textContent }
                ?.let(::stripWhitespace)?.ifEmpty { null }?.let { typeExampleNumbers[type] = it }
        }

        // Possible-length tables. Each type descriptor may carry <possibleLengths national=".."
        // localOnly=".."/>; the general desc almost never does, so we compute it as the union of
        // the type lengths, exactly as libphonenumber's build tool does.
        val typeLengths = LinkedHashMap<String, List<Int>>()
        val typeLocalLengths = LinkedHashMap<String, List<Int>>()
        for (type in TYPES) {
            val el = firstChild(t, type) ?: continue
            val pl = firstChild(el, "possibleLengths") ?: continue
            parseLengths(pl.attrOrNull("national")).takeIf { it.isNotEmpty() }?.let { typeLengths[type] = it }
            parseLengths(pl.attrOrNull("localOnly")).takeIf { it.isNotEmpty() }?.let { typeLocalLengths[type] = it }
        }
        val explicitGeneral = firstChild(t, "generalDesc")?.let { firstChild(it, "possibleLengths") }
        val generalLengths: List<Int>
        val generalLocalLengths: List<Int>
        if (explicitGeneral != null) {
            generalLengths = parseLengths(explicitGeneral.attrOrNull("national"))
            generalLocalLengths = parseLengths(explicitGeneral.attrOrNull("localOnly"))
                .filterNot { it in generalLengths }
        } else {
            generalLengths = typeLengths.values.flatten().toSortedSet().toList()
            generalLocalLengths = typeLocalLengths.values.flatten().toSortedSet()
                .filterNot { it in generalLengths }
        }

        val (numberFormats, intlNumberFormats) = parseNumberFormats(t)

        regions += Region(
            id = id,
            countryCode = ccStr.toInt(),
            mainCountryForCode = t.getAttribute("mainCountryForCode") == "true",
            internationalPrefix = t.attrOrNull("internationalPrefix")?.let(::stripWhitespace),
            nationalPrefix = t.attrOrNull("nationalPrefix"),
            nationalPrefixForParsing = t.attrOrNull("nationalPrefixForParsing")?.let(::stripWhitespace),
            nationalPrefixTransformRule = t.attrOrNull("nationalPrefixTransformRule"),
            generalPattern = generalPattern,
            leadingDigits = t.attrOrNull("leadingDigits")?.let(::stripWhitespace),
            typePatterns = typePatterns,
            possibleLengths = generalLengths,
            possibleLengthsLocalOnly = generalLocalLengths,
            typePossibleLengths = typeLengths,
            typePossibleLengthsLocalOnly = typeLocalLengths,
            numberFormats = numberFormats,
            intlNumberFormats = intlNumberFormats,
            preferredExtnPrefix = t.attrOrNull("preferredExtnPrefix"),
            preferredInternationalPrefix = t.attrOrNull("preferredInternationalPrefix")?.let(::stripWhitespace),
            noInternationalDialling = typePattern(t, "noInternationalDialling"),
            mobileNumberPortableRegion = t.getAttribute("mobileNumberPortableRegion") == "true",
            typeExampleNumbers = typeExampleNumbers,
            exampleNumber = exampleFor(t),
        )
    }

    // Non-geographical entities can repeat id "001"; key by a unique key so
    // geographic regions are never clobbered. The calling-code maps must point at
    // the SAME (possibly deduped) key, or metadata lookups for those codes miss.
    val byId = linkedMapOf<String, Region>()
    val ccToMain = linkedMapOf<Int, String>()
    val ccToRegions = linkedMapOf<Int, MutableList<String>>()
    val mainRegionByCc = linkedMapOf<Int, Region>()
    var dupes = 0
    for (r in regions) {
        val key = if (byId.containsKey(r.id)) { dupes++; "${r.id}_${r.countryCode}" } else r.id
        byId[key] = r
        val list = ccToRegions.getOrPut(r.countryCode) { mutableListOf() }
        if (r.mainCountryForCode) list.add(0, key) else list.add(key) // main region first
        if (r.mainCountryForCode || r.countryCode !in ccToMain) {
            ccToMain[r.countryCode] = key
            mainRegionByCc[r.countryCode] = r
        }
    }

    writeMetadata(mainDir, byId, ccToMain, ccToRegions)
    val exampleCount = writeExamples(testDir, mainRegionByCc)

    println(
        "Wrote metadata (${byId.size} regions, ${ccToMain.size} calling codes; " +
            "skipped $skipped, dupes $dupes) and $exampleCount conformance examples."
    )
}

private fun writeMetadata(
    dir: File,
    byId: Map<String, Region>,
    ccToMain: Map<Int, String>,
    ccToRegions: Map<Int, List<String>>,
) {
    val sb = StringBuilder()
    sb.appendLine(DERIVED_HEADER)
    sb.appendLine("package io.github.aughtone.phonenumber")
    sb.appendLine()
    sb.appendLine("// GENERATED FILE — do not edit by hand.")
    sb.appendLine("// Produced by :metadata-generator from the pinned libphonenumber metadata XML.")
    sb.appendLine("// The embedded epoch is recorded in METADATA_VERSION. Regions: ${byId.size}.")
    sb.appendLine()
    // Split the puts across several functions so no single method exceeds the JVM's 64KB
    // bytecode limit (the whole map in one initializer overflows `<clinit>`). Each chunk is its
    // own function; the property initializer just calls them.
    val entries = byId.entries.toList()
    // Small chunks: with display-format templates embedded, a region's initializer is much larger,
    // so few-per-function keeps each `fillMetadataN` well under the JVM's 64KB bytecode limit.
    val chunkSize = 5
    val chunkCount = (entries.size + chunkSize - 1) / chunkSize
    sb.appendLine("internal val GENERATED_METADATA: Map<String, PhoneMetadata> = buildMap {")
    for (i in 0 until chunkCount) sb.appendLine("    fillMetadata$i(this)")
    sb.appendLine("}")
    sb.appendLine()
    for (i in 0 until chunkCount) {
        sb.appendLine("private fun fillMetadata$i(m: MutableMap<String, PhoneMetadata>) {")
        for (j in i * chunkSize until minOf((i + 1) * chunkSize, entries.size)) {
            val (key, r) = entries[j]
            sb.append("    m.put(").append(kstr(key)).append(", PhoneMetadata(")
            sb.append("id = ").append(kstr(r.id))
            sb.append(", countryCode = ").append(r.countryCode)
            sb.append(", mainCountryForCode = ").append(r.mainCountryForCode)
            sb.append(", internationalPrefix = ").append(knull(r.internationalPrefix))
            sb.append(", nationalPrefix = ").append(knull(r.nationalPrefix))
            sb.append(", nationalPrefixForParsing = ").append(knull(r.nationalPrefixForParsing))
            sb.append(", nationalPrefixTransformRule = ").append(knull(r.nationalPrefixTransformRule))
            sb.append(", generalNationalNumberPattern = ").append(kstr(r.generalPattern))
            sb.append(", leadingDigits = ").append(knull(r.leadingDigits))
            sb.append(", typePatterns = ").append(typeMap(r.typePatterns))
            sb.append(", possibleLengths = ").append(intList(r.possibleLengths))
            sb.append(", possibleLengthsLocalOnly = ").append(intList(r.possibleLengthsLocalOnly))
            sb.append(", typePossibleLengths = ").append(lengthMap(r.typePossibleLengths))
            sb.append(", typePossibleLengthsLocalOnly = ").append(lengthMap(r.typePossibleLengthsLocalOnly))
            sb.append(", numberFormats = ").append(nfList(r.numberFormats))
            sb.append(", intlNumberFormats = ").append(nfList(r.intlNumberFormats))
            sb.append(", preferredExtnPrefix = ").append(knull(r.preferredExtnPrefix))
            sb.append(", preferredInternationalPrefix = ").append(knull(r.preferredInternationalPrefix))
            sb.append(", noInternationalDialling = ").append(knull(r.noInternationalDialling))
            sb.append(", mobileNumberPortableRegion = ").append(r.mobileNumberPortableRegion)
            sb.append(", typeExampleNumbers = ").append(typeMap(r.typeExampleNumbers))
            sb.appendLine("))")
        }
        sb.appendLine("}")
        sb.appendLine()
    }
    sb.appendLine("internal val COUNTRY_CODE_TO_MAIN_REGION: Map<Int, String> = buildMap {")
    for ((cc, region) in ccToMain) sb.appendLine("    put($cc, ${kstr(region)})")
    sb.appendLine("}")
    sb.appendLine()
    sb.appendLine("internal val COUNTRY_CODE_TO_REGIONS: Map<Int, List<String>> = buildMap {")
    for ((cc, list) in ccToRegions) {
        sb.appendLine("    put($cc, listOf(${list.joinToString(", ") { kstr(it) }}))")
    }
    sb.appendLine("}")
    File(dir, "GeneratedMetadata.kt").writeText(sb.toString())
}

private fun writeExamples(dir: File, mainRegionByCc: Map<Int, Region>): Int {
    val examples = mainRegionByCc.values.filter { !it.exampleNumber.isNullOrEmpty() }
    val sb = StringBuilder()
    sb.appendLine(ORIGINAL_HEADER)
    sb.appendLine("package io.github.aughtone.phonenumber")
    sb.appendLine()
    sb.appendLine("// GENERATED FILE — do not edit by hand.")
    sb.appendLine("// Per-region example numbers (main region of each calling code) from the")
    sb.appendLine("// pinned metadata, used as a cross-target byte-stability conformance corpus.")
    sb.appendLine()
    sb.appendLine("internal data class MetadataExample(")
    sb.appendLine("    val region: String,")
    sb.appendLine("    val countryCode: Int,")
    sb.appendLine("    val nationalNumber: String,")
    sb.appendLine(")")
    sb.appendLine()
    sb.appendLine("internal val METADATA_EXAMPLES: List<MetadataExample> = listOf(")
    for (r in examples) {
        sb.appendLine("    MetadataExample(${kstr(r.id)}, ${r.countryCode}, ${kstr(r.exampleNumber!!)}),")
    }
    sb.appendLine(")")
    File(dir, "GeneratedExamples.kt").writeText(sb.toString())
    return examples.size
}

/**
 * Parse a territory's `<availableFormats>` into (nationalFormats, intlFormats), faithfully porting
 * libphonenumber's `BuildMetadataFromXml.loadAvailableFormats` / `loadInternationalFormat`:
 *  - `$NP`/`$FG` in the national-prefix rule and `$FG`/`$NP` in the carrier rule are expanded here.
 *  - each `<numberFormat>` inherits the territory-level prefix / carrier / optional-when-formatting
 *    rules unless it overrides them.
 *  - the intl list mirrors the national list, but a format defaults to the national one when it has
 *    no `<intlFormat>`, is dropped when its `<intlFormat>` is "NA", and the whole intl list is
 *    cleared when no format defined an explicit `<intlFormat>` (the common case).
 */
private fun parseNumberFormats(t: Element): Pair<List<Nf>, List<Nf>> {
    val available = firstChild(t, "availableFormats") ?: return emptyList<Nf>() to emptyList()
    val nationalPrefix = t.attrOrNull("nationalPrefix") ?: ""

    val territoryNpf = t.attrOrNull("nationalPrefixFormattingRule")?.let { expandNpRule(it, nationalPrefix) }
    val territoryNpOpt = t.getAttribute("nationalPrefixOptionalWhenFormatting") == "true"
    val territoryCarrier = t.attrOrNull("carrierCodeFormattingRule")?.let { expandCarrierRule(it, nationalPrefix) }

    val nodes = available.getElementsByTagName("numberFormat")
    val national = mutableListOf<Nf>()
    val intl = mutableListOf<Nf>()
    var hasExplicitIntl = false
    for (i in 0 until nodes.length) {
        val nfe = nodes.item(i) as Element
        val pattern = stripWhitespace(nfe.getAttribute("pattern"))
        val format = firstChild(nfe, "format")?.textContent?.trim() ?: continue
        val leadingDigits = leadingDigitsOf(nfe)
        val npf = if (nfe.hasAttribute("nationalPrefixFormattingRule")) {
            expandNpRule(nfe.getAttribute("nationalPrefixFormattingRule"), nationalPrefix)
        } else {
            territoryNpf
        }
        val npOpt = if (nfe.hasAttribute("nationalPrefixOptionalWhenFormatting")) {
            nfe.getAttribute("nationalPrefixOptionalWhenFormatting") == "true"
        } else {
            territoryNpOpt
        }
        val carrier = if (nfe.hasAttribute("carrierCodeFormattingRule")) {
            expandCarrierRule(nfe.getAttribute("carrierCodeFormattingRule"), nationalPrefix)
        } else {
            territoryCarrier
        }
        val nat = Nf(pattern, format, leadingDigits, npf?.ifEmpty { null }, npOpt, carrier?.ifEmpty { null })
        national += nat

        // International format for this entry.
        val intlNodes = nfe.getElementsByTagName("intlFormat")
        if (intlNodes.length == 0) {
            intl += nat // defaults to the national format (national-prefix rule is unused for intl)
        } else {
            hasExplicitIntl = true
            val intlText = (intlNodes.item(0) as Element).textContent.trim()
            if (intlText != "NA") {
                intl += Nf(pattern, intlText, leadingDigits, null, false, null)
            }
        }
    }
    return national to (if (hasExplicitIntl) intl else emptyList())
}

private fun leadingDigitsOf(nfe: Element): List<String> {
    val nodes = nfe.getElementsByTagName("leadingDigits")
    val out = mutableListOf<String>()
    for (i in 0 until nodes.length) out += stripWhitespace((nodes.item(i) as Element).textContent)
    return out
}

/** Expand a national-prefix formatting rule: `$NP` → national prefix, `$FG` → `$1` (upstream order). */
private fun expandNpRule(raw: String, nationalPrefix: String): String =
    raw.replaceFirst("\$NP", nationalPrefix).replaceFirst("\$FG", "\$1")

/** Expand a carrier-code formatting rule: `$FG` → `$1`, `$NP` → national prefix (upstream order). */
private fun expandCarrierRule(raw: String, nationalPrefix: String): String =
    raw.replaceFirst("\$FG", "\$1").replaceFirst("\$NP", nationalPrefix)

/** national-number pattern text of a child descriptor (e.g. "fixedLine"), whitespace stripped. */
private fun typePattern(t: Element, type: String): String? =
    firstChild(t, type)
        ?.let { firstChild(it, "nationalNumberPattern")?.textContent }
        ?.let(::stripWhitespace)
        ?.ifEmpty { null }

private fun exampleFor(t: Element): String? {
    val fixed = firstChild(t, "fixedLine")?.let { firstChild(it, "exampleNumber")?.textContent }
    val mobile = firstChild(t, "mobile")?.let { firstChild(it, "exampleNumber")?.textContent }
    return (fixed ?: mobile)?.let { stripWhitespace(it) }?.ifEmpty { null }
}

private fun firstChild(parent: Element, tag: String): Element? {
    val children = parent.getElementsByTagName(tag)
    return if (children.length > 0) children.item(0) as Element else null
}

private fun Element.attrOrNull(name: String): String? =
    if (hasAttribute(name)) getAttribute(name) else null

private fun stripWhitespace(s: String): String = s.filterNot { it.isWhitespace() }

/**
 * Parse a libphonenumber possibleLengths spec — comma-separated tokens, each either a single
 * length ("10") or an inclusive range ("[6-8]") — into a sorted, distinct list of ints.
 */
private fun parseLengths(spec: String?): List<Int> {
    if (spec.isNullOrEmpty()) return emptyList()
    val out = sortedSetOf<Int>()
    for (token in spec.split(",")) {
        val t = token.trim()
        if (t.isEmpty()) continue
        if (t.startsWith("[") && t.endsWith("]")) {
            val (lo, hi) = t.substring(1, t.length - 1).split("-").map { it.trim().toInt() }
            for (n in lo..hi) out.add(n)
        } else {
            out.add(t.toInt())
        }
    }
    return out.toList()
}

private fun intList(xs: List<Int>): String =
    if (xs.isEmpty()) "emptyList()" else "listOf(${xs.joinToString(", ")})"

private fun strList(xs: List<String>): String =
    if (xs.isEmpty()) "emptyList()" else "listOf(${xs.joinToString(", ") { kstr(it) }})"

private fun nfList(xs: List<Nf>): String =
    if (xs.isEmpty()) {
        "emptyList()"
    } else {
        "listOf(" + xs.joinToString(", ") { nf ->
            "NumberFormat(pattern = ${kstr(nf.pattern)}, format = ${kstr(nf.format)}, " +
                "leadingDigitsPatterns = ${strList(nf.leadingDigits)}, " +
                "nationalPrefixFormattingRule = ${knull(nf.nationalPrefixFormattingRule)}, " +
                "nationalPrefixOptionalWhenFormatting = ${nf.nationalPrefixOptionalWhenFormatting}, " +
                "domesticCarrierCodeFormattingRule = ${knull(nf.domesticCarrierCodeFormattingRule)})"
        } + ")"
    }

private fun lengthMap(m: Map<String, List<Int>>): String =
    if (m.isEmpty()) "emptyMap()"
    else "mapOf(" + m.entries.joinToString(", ") { "${kstr(it.key)} to ${intList(it.value)}" } + ")"

/** Kotlin string literal with the escapes that matter inside a double-quoted string. */
private fun kstr(s: String): String {
    val body = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
    return "\"$body\""
}

private fun knull(s: String?): String = if (s == null) "null" else kstr(s)

private fun typeMap(types: Map<String, String>): String =
    if (types.isEmpty()) "emptyMap()"
    else "mapOf(" + types.entries.joinToString(", ") { "${kstr(it.key)} to ${kstr(it.value)}" } + ")"

private val DERIVED_HEADER = """
/*
 * Copyright (C) 2009 The Libphonenumber Authors
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
 * Modifications: this file embeds region metadata derived from the original
 * libphonenumber metadata, transcribed to Kotlin by the metadata-generator.
 */""".trimIndent()

private val ORIGINAL_HEADER = """
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
 */""".trimIndent()
