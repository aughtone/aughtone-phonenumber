# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Project scaffold for a pure Kotlin Multiplatform port of Google's [libphonenumber](https://github.com/google/libphonenumber), targeting JVM, Android, iOS, macOS, tvOS, watchOS, Linux, MingW, JS, and wasmJs.
- Apache-2.0 `LICENSE` and a `NOTICE` attributing the derived work to libphonenumber.
- Pinned metadata epoch `libphonenumberMetadata = 9.0.38` (byte-stability epoch; a bump is a new epoch, never an in-place change).
- Maven Central publication and Swift Package Manager (XCFramework) integration via the `vanniktech-mavenPublish` plugin.

### Notes
- The public API (`parse` / format to E.164 / `isValid`, plus a readable metadata-version accessor) and the embedded metadata generator are in development. Metadata is embedded as Kotlin (no runtime resource loading) so it works on wasmJs and native.
