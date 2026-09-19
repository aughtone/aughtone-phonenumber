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
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Accessor/helper-parity harness for AOPH-7 / #19. Compares this port's public accessors and helpers
 * to the 9.0.39 reference across every supported region, calling code and example number. Faithful by
 * construction. JVM-only.
 */
class AccessorsParityTest {

    private val ref = ReferenceUtil.getInstance()

    @Test
    fun supportedSetsMatchUpstream() {
        assertEquals(ref.supportedRegions, PhoneNumberUtil.getSupportedRegions(), "supportedRegions")
        assertEquals(ref.supportedCallingCodes, PhoneNumberUtil.getSupportedCallingCodes(), "supportedCallingCodes")
        assertEquals(
            ref.supportedGlobalNetworkCallingCodes,
            PhoneNumberUtil.getSupportedGlobalNetworkCallingCodes(),
            "supportedGlobalNetworkCallingCodes",
        )
    }

    @Test
    fun regionAndCallingCodeAccessorsMatchUpstream() {
        val gaps = mutableListOf<String>()
        for (region in ref.supportedRegions.sorted()) {
            check(gaps, "countryCodeForRegion $region",
                ref.getCountryCodeForRegion(region), PhoneNumberUtil.getCountryCodeForRegion(region))
            check(gaps, "nddPrefix $region",
                ref.getNddPrefixForRegion(region, false), PhoneNumberUtil.getNddPrefixForRegion(region, false))
            check(gaps, "nddPrefixStripped $region",
                ref.getNddPrefixForRegion(region, true), PhoneNumberUtil.getNddPrefixForRegion(region, true))
            check(gaps, "isNANPA $region", ref.isNANPACountry(region), PhoneNumberUtil.isNANPACountry(region))
            check(gaps, "mnp $region",
                ref.isMobileNumberPortableRegion(region), PhoneNumberUtil.isMobileNumberPortableRegion(region))
        }
        for (cc in ref.supportedCallingCodes.sorted()) {
            check(gaps, "regionForCC $cc", ref.getRegionCodeForCountryCode(cc), PhoneNumberUtil.getRegionCodeForCountryCode(cc))
            check(gaps, "regionsForCC $cc", ref.getRegionCodesForCountryCode(cc), PhoneNumberUtil.getRegionCodesForCountryCode(cc))
            check(gaps, "mobileToken $cc", ReferenceUtil.getCountryMobileToken(cc), PhoneNumberUtil.getCountryMobileToken(cc))
        }
        report(gaps)
    }

    @Test
    fun exampleNumbersMatchUpstream() {
        val gaps = mutableListOf<String>()
        for (region in ref.supportedRegions.sorted()) {
            check(gaps, "example $region", e164(ref.getExampleNumber(region)), e164(PhoneNumberUtil.getExampleNumber(region)))
            check(gaps, "invalidExample $region",
                e164(ref.getInvalidExampleNumber(region)), e164(PhoneNumberUtil.getInvalidExampleNumber(region)))
            for (refType in RefType.values()) {
                if (refType == RefType.UNKNOWN) continue
                val ourType = PhoneNumberUtil.PhoneNumberType.valueOf(refType.name)
                check(gaps, "exampleForType $region/$refType",
                    e164(ref.getExampleNumberForType(region, refType)),
                    e164(PhoneNumberUtil.getExampleNumberForType(region, ourType)))
            }
        }
        for (cc in ref.supportedGlobalNetworkCallingCodes.sorted()) {
            check(gaps, "exampleNonGeo $cc",
                e164(ref.getExampleNumberForNonGeoEntity(cc)), e164(PhoneNumberUtil.getExampleNumberForNonGeoEntity(cc)))
        }
        // Single-arg getExampleNumberForType(type) picks the first region from an unordered set, so
        // the exact number is implementation-defined; assert only that both find one (or neither).
        for (refType in RefType.values()) {
            if (refType == RefType.UNKNOWN) continue
            val ourType = PhoneNumberUtil.PhoneNumberType.valueOf(refType.name)
            check(gaps, "exampleForType(noRegion) $refType",
                ref.getExampleNumberForType(refType) != null,
                PhoneNumberUtil.getExampleNumberForType(ourType) != null)
        }
        report(gaps)
    }

    @Test
    fun numberHelpersMatchUpstreamForExamples() {
        val gaps = mutableListOf<String>()
        for (region in ref.supportedRegions.sorted()) {
            val example = ref.getExampleNumber(region) ?: continue
            val e164 = ref.format(example, RefFormat.E164)
            val refPn = try { ref.parse(e164, region) } catch (e: Exception) { continue }
            val ourPn = try {
                PhoneNumberUtil.parse(e164, region, libphonenumberCompat = true)
            } catch (e: PhoneNumberUtil.NumberParseException) { continue }

            check(gaps, "regionForNumber $e164", ref.getRegionCodeForNumber(refPn), PhoneNumberUtil.getRegionCodeForNumber(ourPn))
            check(gaps, "nsn $e164", ref.getNationalSignificantNumber(refPn), PhoneNumberUtil.getNationalSignificantNumber(ourPn))
            check(gaps, "isGeographical $e164", ref.isNumberGeographical(refPn), PhoneNumberUtil.isNumberGeographical(ourPn))
            check(gaps, "canBeIntlDialled $e164", ref.canBeInternationallyDialled(refPn), PhoneNumberUtil.canBeInternationallyDialled(ourPn))
            check(gaps, "lengthGeoAreaCode $e164",
                ref.getLengthOfGeographicalAreaCode(refPn), PhoneNumberUtil.getLengthOfGeographicalAreaCode(ourPn))
            check(gaps, "lengthNdc $e164",
                ref.getLengthOfNationalDestinationCode(refPn), PhoneNumberUtil.getLengthOfNationalDestinationCode(ourPn))
        }
        report(gaps)
    }

    @Test
    fun alphaAndNormalizeHelpersMatchUpstream() {
        val gaps = mutableListOf<String>()
        val alphaCases = listOf("1800 FLOWERS", "1-800-flowers", "0800 DDA 005", "1234567", "abc", "+1 (800) FLOWERS")
        for (s in alphaCases) {
            check(gaps, "isAlpha \"$s\"", ref.isAlphaNumber(s), PhoneNumberUtil.isAlphaNumber(s))
            check(gaps, "convertAlpha \"$s\"",
                ReferenceUtil.convertAlphaCharactersInNumber(s), PhoneNumberUtil.convertAlphaCharactersInNumber(s))
            check(gaps, "diallableOnly \"$s\"",
                ReferenceUtil.normalizeDiallableCharsOnly(s), PhoneNumberUtil.normalizeDiallableCharsOnly(s))
        }
        report(gaps)
    }

    @Test
    fun invalidRegionAccessorsMatchUpstream() {
        // Unknown / non-geo region codes behave like upstream (0, null, false, empty).
        for (bad in listOf("ZZ", "001", "", "XY")) {
            assertEquals(ref.getCountryCodeForRegion(bad), PhoneNumberUtil.getCountryCodeForRegion(bad), "cc $bad")
            assertEquals(ref.getNddPrefixForRegion(bad, false), PhoneNumberUtil.getNddPrefixForRegion(bad, false), "ndd $bad")
            assertEquals(ref.isNANPACountry(bad), PhoneNumberUtil.isNANPACountry(bad), "nanpa $bad")
            assertEquals(ref.isMobileNumberPortableRegion(bad), PhoneNumberUtil.isMobileNumberPortableRegion(bad), "mnp $bad")
            assertEquals(e164(ref.getExampleNumber(bad)), e164(PhoneNumberUtil.getExampleNumber(bad)), "example $bad")
        }
    }

    private fun e164(pn: RefPhoneNumber?): String? = pn?.let { ref.format(it, RefFormat.E164) }
    private fun e164(pn: PhoneNumber?): String? = pn?.formatToE164()

    private fun check(gaps: MutableList<String>, label: String, refVal: Any?, ourVal: Any?) {
        if (refVal != ourVal) gaps += "[$label] ours=$ourVal ref=$refVal"
    }

    private fun report(gaps: List<String>) {
        if (gaps.isNotEmpty()) fail("${gaps.size} accessor gap(s):\n" + gaps.take(60).joinToString("\n"))
    }
}
