# Porting PhoneNumberMatcher

RAD-0002 · 2026-09-19 · Status: open
Keywords: PhoneNumberMatcher, findNumbers, find phone numbers in text, extract numbers from free text, do we need the matcher, scan text for phone numbers, should aughtone-phonenumber find numbers

Placed out of scope by [ADR-0001](../decisions/ADR-0001-scope-of-the-libphonenumber-port.md). This record frames what bringing it in would cost. No decision.

## Question

Should this library port libphonenumber's `PhoneNumberMatcher` / `findNumbers` — the component that scans free text and returns the phone numbers found in it — and what does that require?

## Trail

**What it is.** `findNumbers` walks arbitrary text, proposes candidate substrings that look like phone numbers, and validates each, returning matches with offsets. Upstream's `PhoneNumberMatcherTest` (46) plus `PhoneNumberMatchTest` (2) come to 48 methods, none addressed here.

**What it needs that we do not have.** It is the heaviest of the three. It depends on a broad candidate-extraction regime (leniency levels — POSSIBLE, VALID, STRICT_GROUPING, EXACT_GROUPING), on grouping checks that compare how a candidate is punctuated against the region's formatting, and thus on the same display-format metadata `AsYouTypeFormatter` needs ([RAD-0001](RAD-0001-porting-asyoutypeformatter.md)). It leans hardest on regex behaviour — precisely the area where Kotlin/Native and Kotlin/Wasm diverge (KT-89187), which the project already works around with its own `PhonePattern` for the metadata subset. Matcher-grade patterns are larger and more backtracking-heavy than anything `PhonePattern` handles today, so the matcher would stress that engine far past its current use.

**Byte-stability.** The matches feed downstream parsing, so a found number would still canonicalize through the byte-stable path; the *set of matches found in a given text* is not itself a canonical form, but a consumer that hashes what it finds would depend on the matcher being deterministic across targets — which is exactly the KT-89187 risk, amplified.

**Who wants it.** No current consumer. It is the classic "find phone numbers in a document" feature, useful for scanning, not for normalizing a known number.

## Open questions

- Can `PhonePattern` carry matcher-grade patterns across all targets, or would this reopen the regex-portability problem the port exists to avoid?
- Is the demand real enough to justify the largest of the three ports plus a display-format metadata slice?

## Recommendation

Leave out of scope. Of the three, this is both the largest and the one most likely to reintroduce the cross-target regex divergence the project was built to escape. Revisit only with a concrete consumer and a plan for the matcher's regex load on Native/Wasm.
