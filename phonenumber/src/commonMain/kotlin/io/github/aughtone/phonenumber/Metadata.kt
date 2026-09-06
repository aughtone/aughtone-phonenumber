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
 * The pinned libphonenumber metadata release embedded in this build — the
 * byte-stability *epoch*. Normalization output (canonical E.164) is frozen
 * within a released version; a metadata bump is a new epoch shipped as a new
 * library version, never an in-place change.
 *
 * This value is the source of truth surfaced to consumers so they can record
 * which epoch produced a given normalized value. It must stay in sync with
 * `libphonenumberMetadata` in `gradle/libs.versions.toml`.
 *
 * This file is original to this repository (not derived from Google's work), so
 * it carries only the Aught One copyright and no modification notice.
 */
public const val METADATA_VERSION: String = "9.0.38"
