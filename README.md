# Aught One - libphonenumber (Kotlin Multiplatform)

A pure Kotlin Multiplatform (KMP) port of Google's [libphonenumber](https://github.com/google/libphonenumber), focused on parsing phone numbers, formatting them to canonical [E.164](https://en.wikipedia.org/wiki/E.164), and validating them — across every KMP target, including wasmJs and iOS where JVM-style resource loading is not available.

> **Attribution:** This library is a Kotlin port of the original Java implementation maintained under Google's libphonenumber project. The parsing logic, formatting, validation, and the region metadata are derived from the [official google/libphonenumber repository](https://github.com/google/libphonenumber) and are used under the Apache License, Version 2.0. See [`NOTICE`](NOTICE) for the full attribution. This port is not affiliated with or endorsed by Google.

> **Status:** Pre-1.0 — the `0.0.x` line is the alpha series. The public API described below is the contract we are building to and may change before `1.0`; the embedded metadata (below) is pinned and its output will not change within a released version.

## Scope — what this port covers

This is a port of libphonenumber's **number parsing, validation, and formatting** — the `PhoneNumberUtil` surface — and not the whole library. The boundary is deliberate and is recorded in [ADR-0001](docs/knowledge/decisions/ADR-0001-scope-of-the-libphonenumber-port.md).

**Covered:** parsing (`+` international, IDD-dialed, and national forms) to a national significant number; formatting to canonical E.164; a validity check; and deterministic Unicode digit normalization. Extension parsing is in progress ([#5](https://github.com/aughtone/aughtone-phonenumber/issues/5)).

**Not covered** — these libphonenumber features are intentionally out of scope; if you need one, it is not here:

- **AsYouTypeFormatter** — live formatting as a user types.
- **PhoneNumberMatcher / `findNumbers`** — finding phone numbers in free text.
- **ShortNumberInfo** — emergency numbers and short codes.

**The port contract.** With `libphonenumberCompat = true`, behaviour across the covered surface is byte-identical to upstream, and upstream's own tests for that surface pass. The default (`libphonenumberCompat = false`) may do the more-correct thing (see [Compatibility with libphonenumber](#compatibility-with-libphonenumber)) and is covered by this project's own tests. So: flip the flag and it *is* libphonenumber; leave it and it is libphonenumber-derived but improved — never silently different while claiming parity.

### Why this lives outside Google's repository

Google's libphonenumber does not take language ports into its main repository; community ports are published independently and maintained by their authors. This is one such independent port. Keeping it separate is the arrangement the project expects, not a fork around it.

## Byte-stability and the pinned metadata version

This port embeds a **pinned** slice of libphonenumber metadata. Within a released version, the same input always canonicalizes to the same E.164 bytes. This matters for downstream consumers that derive stable tokens or anchors from the canonical form: a silent change in normalization would invalidate everything already derived.

The pin is a **change detector**, not a permanent freeze. libphonenumber ships metadata updates regularly, and we want the new regions and ranges. So each upstream release is adopted deliberately: we refresh the embedded metadata and re-run the full corpus, checking that **no E.164 we already produce changes**. New regions and wider ranges (input we did not accept before) come in freely; a change to an *existing* output is the one thing the refresh guards against, and is handled as a called-out, breaking change rather than shipped silently.

- Embedded metadata version: **9.0.39**
- A refresh that changes no existing output is a normal version bump; one that would change existing output is flagged in the release notes so consumers who derive stable tokens can decide when to adopt it.
- The version is readable at runtime (see Usage) so consumers can record which metadata produced a given value.

## Why this ships its own regex matcher

libphonenumber's parsing and validation are regex-driven, and `kotlin.text.Regex` is **not one engine** across Kotlin Multiplatform targets: JVM uses `java.util.regex`, JS uses the host `RegExp`, and **Kotlin/Native and Kotlin/Wasm share a Kotlin implementation** that mis-backtracks non-capturing groups with unequal-length alternatives — a construct libphonenumber patterns use heavily. The effect is that the *same* number can validate on JVM/JS yet be rejected on iOS or wasmJs, which silently breaks byte-stable output on exactly the targets that motivated this port.

Because that would defeat the purpose of a versioned, byte-stable normalizer, this library uses its **own small deterministic regex matcher** (in `commonMain`, for the metadata pattern subset) on every target. One engine everywhere means identical output by construction, independent of platform regex quirks.

Upstream issues:
- [KT-89187](https://youtrack.jetbrains.com/issue/KT-89187) — Kotlin/Native and Kotlin/Wasm: `Regex` fails to backtrack across unequal-length alternatives in a group (differs from JVM and Kotlin/JS). Filed by this project.
- [KT-57906](https://youtrack.jetbrains.com/issue/KT-57906) — K/N: behaviour differs from JVM for regexes with backreferences (related; same non-JVM regex engine).

## Compatibility with libphonenumber

This is a port, so upstream libphonenumber is the reference. Where this library's `parse` behaviour differs, the difference is deliberate and listed here. Each difference makes the port *more* correct or safer without changing the E.164 it produces for ordinary numbers.

### Differences from upstream (safe by default)

- **Parse errors never contain the input.** `NumberParseException` carries a machine-readable `ErrorType` and a fixed message string; it never interpolates the number or region into the message. Logging a failed parse cannot leak a phone number. (Upstream messages embed the offending value.)
- **"+" detection is deterministic across targets.** Whether a number is treated as international is decided by scanning code points for the first `+` (ASCII `U+002B` or fullwidth `U+FF0B`) ahead of the first decimal digit — it does not call `String.trim()` or `Char.isWhitespace()`, whose tables vary by platform and Unicode version. The same input therefore classifies identically on every target.
- **All Unicode decimal digits are recognised.** Digit normalization accepts every `General_Category=Nd` code point in a frozen Unicode table (see below), including supplementary (`> U+FFFF`) digits such as the mathematical bold digits. This is a strict superset of what upstream accepts: every input upstream normalizes, this normalizes to the same ASCII digits, and a few inputs upstream silently drops (supplementary-plane digits, because it reads one UTF-16 `char` at a time) are also handled. It accepts more input; it never changes the output for input upstream already accepted.
- **Ambiguous direct-dial (Durchwahl) numbers are refused, not silently folded.** German and Austrian numbers write a direct-dial extension with a hyphen or space — `+49 30 12345678-12`, `+43 1 58058-0`. Upstream folds those digits into the national number, yielding a *different, valid-looking* number (`+49301234567812`). When the input carries a trailing digit group separated by formatting, this library asks whether the number is valid both **with** and **without** that group; if both are valid the input is genuinely ambiguous and `parse` throws `NOT_A_NUMBER` rather than invent a number. If only the base is valid, the trailing group is taken as the extension; otherwise the number is left unchanged. Fixed-length plans (US, GB, …) are unaffected, because the folded reading is invalid there. Pass `libphonenumberCompat = true` to fold like upstream instead.

The digit table is pinned to a specific Unicode version so the set of accepted digits is itself byte-stable and does not follow the runtime's Unicode version:

- Frozen Unicode version: **17.0.0**, readable at runtime as `PhoneNumberUtil.digitUnicodeVersion`.

### Opting into exact upstream digit behaviour

If you need digit handling identical to libphonenumber — for example to reproduce its output bit-for-bit, including its dropping of supplementary-plane digits — pass `libphonenumberCompat = true`:

```kotlin
// Default: full Unicode 17.0.0 decimal digits (recommended).
PhoneNumberUtil.parse(input, defaultRegion = "US")

// Exact upstream digit behaviour: BMP digits only, supplementary digits stripped.
PhoneNumberUtil.parse(input, defaultRegion = "US", libphonenumberCompat = true)
```

The flag is a per-call argument, defaulting to `false` (the more-correct behaviour). It is never a mutable global: output stays a pure function of `(input, defaultRegion, libphonenumberCompat)`, so nothing elsewhere in a process can change how a given call normalizes. `isValid` takes the same flag.

The digit helpers are public for consumers that need the same frozen normalization without going through `parse`:

```kotlin
normalizeDigitsOnly("(650) 253-0000")        // "6502530000"
decimalDigitValue('٥'.code)                   // 5  (Arabic-Indic five)
DIGIT_UNICODE_VERSION                          // "17.0.0"
```

### One upstream behaviour deliberately not reproduced

- **A second number is not truncated** — upstream cuts the input at the start of an apparent second number; this library does not. If your inputs may contain two numbers, split them before calling `parse`. This is tracked as a known difference rather than a bug.

(Alphabetic vanity numbers, extensions, and the RFC 3966 `tel:` form *are* handled — the extension is returned on the parsed number and left out of the E.164.)

## Features

- **100% pure Kotlin** in `commonMain` — no `expect`/`actual` platform wrappers for the core.
- **Metadata embedded as Kotlin** — compiled into the binary on every target, with **no runtime resource loading** (the approach that lets wasmJs and native work where classpath/resource loading does not).
- **Byte-stable, versioned output** — E.164 normalization is stable within a released version and refreshed only when existing output is unchanged.
- **Multiplatform:** JVM, Android, iOS, macOS, tvOS, watchOS, Linux, MingW, JS, and wasmJs.

## Installation

### Kotlin Multiplatform / JVM / Android (Maven Central)

```kotlin
dependencies {
    implementation("io.github.aughtone:phonenumber:0.0.2")
}
```

### iOS / Swift Package Manager (Xcode)

This library is distributed to Swift as a precompiled XCFramework.

1. In Xcode: **File > Add Package Dependencies**.
2. Enter the repository URL: `https://github.com/aughtone/aughtone-phonenumber`
3. While on the `0.0.x` line, select **Exact Version** — pre-1.0 releases may make breaking changes.
4. Add the `PhoneNumber` product to your target.

## Usage

```kotlin
import io.github.aughtone.phonenumber.PhoneNumberUtil

// Parse a number for a default region and format it to canonical E.164.
val e164 = PhoneNumberUtil.parse("(650) 253-0000", defaultRegion = "US").formatToE164()
println(e164) // +16502530000

// Validate (a default region is always required).
val ok = PhoneNumberUtil.isValid("+16502530000", defaultRegion = "US") // true

// The embedded metadata version that produced this output.
println(PhoneNumberUtil.metadataVersion) // 9.0.39

// The Unicode version of the frozen decimal-digit table (see Compatibility below).
println(PhoneNumberUtil.digitUnicodeVersion) // 17.0.0
```

See [Compatibility with libphonenumber](#compatibility-with-libphonenumber) for the `libphonenumberCompat` flag and the ways this port's `parse` differs from upstream.

## License

This project is licensed under the Apache License, Version 2.0. See the [`LICENSE`](LICENSE) file for details, and [`NOTICE`](NOTICE) for attribution of the derived work.
