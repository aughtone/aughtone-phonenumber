# Porting AsYouTypeFormatter

RAD-0001 · 2026-09-19 · Status: open
Keywords: as you type formatter, AYT, live formatting, format while typing, do we need AsYouTypeFormatter, phone input field, partial number formatting, should aughtone-phonenumber format as you type

Placed out of scope by [ADR-0001](../decisions/ADR-0001-scope-of-the-libphonenumber-port.md). This record frames what bringing it in would cost. No decision.

## Question

Should this library port libphonenumber's `AsYouTypeFormatter` — the component that formats a number progressively as a user types each digit — and if so, what does it actually require?

## Trail

**What it is.** `AsYouTypeFormatter` takes one input character at a time and returns the number formatted so far, choosing a formatting template from the region's metadata and re-selecting it as more digits arrive. Upstream's `AsYouTypeFormatterTest` has 33 test methods, none of which this project currently addresses.

**What it needs that we do not have.** It is driven by the metadata `numberFormat` / `availableFormats` entries — the `pattern`, `format`, and `leadingDigits` used for *display* grouping. Our generator embeds the validation patterns and prefixes but not the display-format templates, so the metadata slice would have to grow. It is also stateful in a way the rest of the port is not: it holds partial-input state across calls, where `parse`/`format`/`isValid` are pure functions.

**Byte-stability.** AYT produces human-facing display strings, not the canonical E.164 the byte-stability guarantee is about. Porting it would not touch the token path, so it carries none of the epoch risk — but it does add a metadata surface (the format templates) whose changes we would then be tracking.

**Who wants it.** The founding consumer (blind tokenization) has no use for live formatting — it needs canonical E.164. AYT is a UI concern, most valuable to an app with a phone-input field. No consumer has asked for it yet.

## Open questions

- Is there a real consumer, or is this hypothetical? A UI-only feature with no caller is the weakest case for widening scope.
- Would it ship in this artifact or a separate `:phonenumber-format`-style module, given it needs display-format metadata the core does not?
- Does the display-format metadata carry its own stability expectations we would have to state?

## Recommendation

Leave out of scope until a consumer needs it. If one does, treat it as its own module with its own metadata slice, not an addition to the core parse/validate/format surface.
