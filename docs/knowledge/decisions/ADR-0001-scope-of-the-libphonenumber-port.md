# Scope of the libphonenumber port

ADR-0001 · 2026-09-19 · Status: accepted
Keywords: is this the whole library, what does the port cover, why no AsYouTypeFormatter, why no PhoneNumberMatcher, why no findNumbers, why no ShortNumberInfo, is it really a port, do all upstream tests pass, no skipped tests, libphonenumberCompat, extension parsing, does it match libphonenumber exactly, can I trust the output

## Context

The README calls this a "port" of Google's libphonenumber. That word sets a contract a reader relies on: **the same behaviour as the original.** A thing called a port that quietly returns different values than the original cannot be trusted, because the reader cannot tell which calls agree and which do not.

The `0.0.1` and `0.0.2` releases did not meet that contract, and presented as if they did. They shipped only the E.164 normalization path — parse to national significant number, format to E.164, and a general-pattern validity check — while the README described a port. A review from the `aughtone-normalize` project (bearcave BC.160–BC.162) found that `parse()` silently folds a phone extension into the national number: `+1 212 555 0123 x123` becomes `+12125550123123`, a valid-looking wrong number, rather than either capturing the extension (as upstream does) or refusing. The cause is that upstream's whole input-extraction stage — `extractPossibleNumber`, `maybeStripExtension`, `convertAlphaCharactersInNumber` — was never ported; a single "keep the decimal digits" function stood in for it. The gap was invisible because the tests only fed well-formed example numbers, never the messy input that stage exists to handle.

Two forces pull against each other.

**A port must not output different values.** This is the trust anchor and is not negotiable for anything we present as a port.

**libphonenumber is far larger than the E.164 path.** Its test suite is 284 methods, and they are not all `PhoneNumberUtil`. Around 146 cover number parse / validate / format; around 109 cover three separate subsystems this project has never built — `AsYouTypeFormatter` (33), `PhoneNumberMatcher` / findNumbers (48), `ShortNumberInfo` (28); and around 40 test a runtime metadata **resource-loader** we deliberately replaced with embedded metadata, which is the reason the port works at all on wasmJs and native.

Non-negotiable constraints already in force: byte-stable E.164 output within a released version (downstream derives tokens from it); metadata embedded as Kotlin with no runtime resource loading; one regex engine on every target via the project's own `PhonePattern` (see the README on KT-89187).

"Ship a port people can trust, with no skipped tests" is therefore only achievable once the word "port" is scoped to something we can actually make true.

## Decision

**This is a port of libphonenumber's number parsing, validation, and formatting — the `PhoneNumberUtil` surface — and nothing else.** Within that scope:

- **`libphonenumberCompat = true` reproduces upstream byte-for-byte, and every applicable upstream test passes in that mode.** Compat mode is the trust anchor: flip the flag and it *is* libphonenumber. The contract spans every feature we implement — extensions, national-prefix handling, validity, formatting — not only digit normalization.
- **Default mode (`libphonenumberCompat = false`) may do the more-correct thing** (full Unicode digits today; the extension capture and the Durchwahl guard later) and is covered by our own, clearly-labelled non-upstream tests.
- **No skipped tests in a release, within scope.** Every applicable upstream test either passes or is removed by an explicit architectural exemption recorded here. A skip is never a way to defer a feature the port claims to provide.
- **The three subsystems — `AsYouTypeFormatter`, `PhoneNumberMatcher` (findNumbers), `ShortNumberInfo` — are out of scope.** They are not skipped tests; their test files are simply not part of this project, because their features are not. Each is explored in a Research record ([RAD-0001](../research/RAD-0001-porting-asyoutypeformatter.md), [RAD-0002](../research/RAD-0002-porting-phonenumbermatcher.md), [RAD-0003](../research/RAD-0003-porting-shortnumberinfo.md)) before any decision to bring it in.
- **Architectural exemptions are listed and justified, and expected to be very few.** The resource-loader tests are the known class: they assert file-based metadata loading that embedded metadata makes impossible by construction.

The README states this scope plainly — what the port covers and what it does not — so "port" is never read as "all of libphonenumber."

**Rejected: port the whole library before the next release.** Re-implementing the as-you-type formatter, the matcher, the short-number subsystem, geocoding, and the full format / type / possible-length surface at once is a multi-month rewrite, and most of it is not what a byte-stable E.164 normalizer's consumers need. It would also block the extension fix behind an enormous unrelated body of work.

**Rejected: keep the reduced subset and keep calling it a port with the improvements on by default.** This is the exact thing that breaks trust — a port that silently outputs different values than the original. The improvements are fine; presenting them as upstream behaviour is not.

**Rejected: flip `libphonenumberCompat` to default `true` (upstream-exact by default).** Considered, because it makes the untouched default a true port. The maintainer chose default = correct, compat = opt-in: a new consumer should get the correct behaviour, and reproducing upstream exactly is the deliberate, opt-in choice. Compat mode as the parity anchor gives the trust guarantee without making every caller inherit upstream's warts.

## Consequences

**Easier.** "Port" becomes a claim we can stand behind and prove: compat mode equals upstream, demonstrated by upstream's own tests with no skips. The silent-gap fear has a definite answer — the coverage matrix in [#7](https://github.com/aughtone/aughtone-phonenumber/issues/7) accounts for all 284 upstream methods, so "have we implemented everything in scope?" is a question with a yes/no answer rather than a hope. Default mode can be more correct without weakening the port claim, because compat mode carries it.

**Harder.** Compat mode is now a whole-surface contract, not just digits: every feature we add needs a compat path that matches upstream exactly, with the upstream tests to prove it. Reaching zero skips within scope means implementing the full parse / validate / format surface — extension parsing ([#5](https://github.com/aughtone/aughtone-phonenumber/issues/5)), alpha/vanity handling, every `format(...)` variant, `getNumberType`, `isPossibleNumber` with possible-length metadata, and a `PhoneNumber` value object carrying extension and raw input — before the next release may claim parity.

**What we gave up.** Whole-library parity. A caller wanting an as-you-type formatter, `findNumbers` over free text, or short-number/emergency handling does not get them here. That is deliberate and is written into the README so the boundary is visible on arrival; whether any of the three is ever brought in is left to their RADs. The `0.0.1`/`0.0.2` releases stand as published, with their divergence documented; this decision governs the next release onward.
