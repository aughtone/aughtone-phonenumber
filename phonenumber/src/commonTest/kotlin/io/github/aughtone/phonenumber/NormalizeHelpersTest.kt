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
import kotlin.test.assertEquals

/**
 * #19 public normalize/convert helpers, cross-target with deterministic expected values taken from
 * libphonenumber's own `testNormalise*` / `testConvertAlphaCharactersInNumber` tests (so no reference
 * is needed and the byte-stable output is checked on every target).
 */
class NormalizeHelpersTest {

    @Test fun normaliseRemovePunctuation() {
        // No alpha → digits only, punctuation and the soft hyphen removed.
        assertEquals("03456234", PhoneNumberUtil.normalize("034-56&+#2­34"))
    }

    @Test fun normaliseReplaceAlphaCharacters() {
        // Three or more letters → keypad conversion, punctuation dropped.
        assertEquals("034426486479", PhoneNumberUtil.normalize("034-I-am-HUNGRY"))
    }

    @Test fun normaliseStripAlphaCharacters() {
        assertEquals("03456234", normalizeDigitsOnly("034-56&+a#234"))
    }

    @Test fun convertAlphaCharactersKeepsPunctuation() {
        assertEquals("1800-222-333", PhoneNumberUtil.convertAlphaCharactersInNumber("1800-ABC-DEF"))
    }

    @Test fun normaliseStripNonDiallableCharacters() {
        assertEquals("03*456+1#234", PhoneNumberUtil.normalizeDiallableCharsOnly("03*4-56&+1a#234"))
    }
}
