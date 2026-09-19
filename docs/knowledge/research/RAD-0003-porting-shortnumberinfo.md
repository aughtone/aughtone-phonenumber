# Porting ShortNumberInfo

RAD-0003 · 2026-09-19 · Status: open
Keywords: ShortNumberInfo, short numbers, emergency numbers, 112 911, premium short codes, do we need short numbers, is this number an emergency number, should aughtone-phonenumber handle short codes

Placed out of scope by [ADR-0001](../decisions/ADR-0001-scope-of-the-libphonenumber-port.md). This record frames what bringing it in would cost. No decision.

## Question

Should this library port libphonenumber's `ShortNumberInfo` — emergency and short-code handling (112, 911, premium short codes, and the like) — and what does it require?

## Trail

**What it is.** `ShortNumberInfo` answers questions about short numbers: is this an emergency number for the region, is it a valid short code, what cost class is it. Upstream's `ShortNumberInfoTest` has 28 methods, none addressed here.

**What it needs that we do not have.** It is driven by an entirely **separate metadata file** — `ShortNumberMetadata.xml`, distinct from `PhoneNumberMetadata.xml` the port embeds. Bringing it in means a second embedded metadata slice, generated the same way, with its own version to pin and its own epoch discipline. The logic itself is close in spirit to what the port already does (match a number against region patterns), so the code is not the hard part — the data is.

**Byte-stability.** Short-number classification is a boolean/enum answer, not a canonical form, so it does not sit on the token path. But if a consumer ever persisted "is emergency" decisions, the short-number metadata version would need the same change-detector discipline the phone metadata already has.

**Who wants it.** No current consumer. Emergency-number awareness matters to a dialer or a safety feature, not to canonicalizing a contact's number for matching. It is the most self-contained of the three and the least entangled with the regex-portability problem.

## Open questions

- Same artifact with a second metadata slice, or a separate `:phonenumber-short` module so callers who do not need it carry no extra data?
- Does it pin to the same libphonenumber metadata release as the phone metadata, moving in lockstep, or version independently?

## Recommendation

Leave out of scope until a consumer needs it. Of the three it is the cleanest to add later — self-contained, data-driven, no regex-portability exposure — so it is the strongest candidate if the suite ever grows a dialer-facing consumer. Treat it as its own metadata slice with its own pinned version when it comes.
