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
 * The pinned libphonenumber metadata release embedded in this build.
 * Normalization output (canonical E.164) is stable within a released version.
 * The pin is a change detector: each upstream release is adopted deliberately,
 * refreshing this metadata only after confirming no existing output changes; a
 * refresh that would change existing output is a called-out breaking change, not
 * an in-place one.
 *
 * This value is the source of truth surfaced to consumers so they can record
 * which metadata produced a given normalized value. It must stay in sync with
 * `libphonenumberMetadata` in `gradle/libs.versions.toml`.
 *
 * This file is original to this repository (not derived from Google's work), so
 * it carries only the Aught One copyright and no modification notice.
 */
public const val METADATA_VERSION: String = "9.0.39"
