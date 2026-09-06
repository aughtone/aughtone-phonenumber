# Aught One - libphonenumber (Kotlin Multiplatform)

A pure Kotlin Multiplatform (KMP) port of Google's [libphonenumber](https://github.com/google/libphonenumber), focused on parsing phone numbers, formatting them to canonical [E.164](https://en.wikipedia.org/wiki/E.164), and validating them — across every KMP target, including wasmJs and iOS where JVM-style resource loading is not available.

> **Attribution:** This library is a Kotlin port of the original Java implementation maintained under Google's libphonenumber project. The parsing logic, formatting, validation, and the region metadata are derived from the [official google/libphonenumber repository](https://github.com/google/libphonenumber) and are used under the Apache License, Version 2.0. See [`NOTICE`](NOTICE) for the full attribution. This port is not affiliated with or endorsed by Google.

> **Status:** Alpha. The public API described below is the contract we are building to; it may still change before `1.0`. The metadata epoch (below) is stable and will not change within a released version.

### Why this lives outside Google's repository

Google's libphonenumber does not take language ports into its main repository; community ports are published independently and maintained by their authors. This is one such independent port. Keeping it separate is the arrangement the project expects, not a fork around it.

## The byte-stability epoch

This port embeds a **pinned** slice of libphonenumber metadata and treats its version as a normalization **epoch**. Within a released version, the same input always canonicalizes to the same E.164 bytes — output is frozen. This matters for downstream consumers that derive stable tokens or anchors from the canonical form: a silent change in normalization would invalidate everything already derived.

- Embedded metadata version (current epoch): **9.0.38**
- A metadata bump is a **new epoch** shipped as a new library version — never an in-place change within a version.
- The epoch is readable at runtime (see Usage) so consumers can record which epoch produced a given value.

## Features

- **100% pure Kotlin** in `commonMain` — no `expect`/`actual` platform wrappers for the core.
- **Metadata embedded as Kotlin** — compiled into the binary on every target, with **no runtime resource loading** (the approach that lets wasmJs and native work where classpath/resource loading does not).
- **Byte-stable, versioned output** — E.164 normalization is frozen per metadata epoch.
- **Multiplatform:** JVM, Android, iOS, macOS, tvOS, watchOS, Linux, MingW, JS, and wasmJs.

## Installation

### Kotlin Multiplatform / JVM / Android (Maven Central)

```kotlin
dependencies {
    implementation("io.github.aughtone:phonenumber:0.0.1-alpha1")
}
```

### iOS / Swift Package Manager (Xcode)

This library is distributed to Swift as a precompiled XCFramework.

1. In Xcode: **File > Add Package Dependencies**.
2. Enter the repository URL: `https://github.com/aughtone/aughtone-phonenumber`
3. For a prerelease tag (e.g. `-alpha1`) select **Exact Version**.
4. Add the `PhoneNumber` product to your target.

## Usage

```kotlin
import io.github.aughtone.phonenumber.PhoneNumberUtil

// Parse a number for a default region and format it to canonical E.164.
val e164 = PhoneNumberUtil.parse("(650) 253-0000", defaultRegion = "US").formatToE164()
println(e164) // +16502530000

// Validate.
val ok = PhoneNumberUtil.isValid("+16502530000") // true

// The metadata epoch that produced this output.
println(PhoneNumberUtil.metadataVersion) // 9.0.38
```

## License

This project is licensed under the Apache License, Version 2.0. See the [`LICENSE`](LICENSE) file for details, and [`NOTICE`](NOTICE) for attribution of the derived work.
