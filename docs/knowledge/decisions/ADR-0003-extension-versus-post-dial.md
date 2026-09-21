# Extension versus post-dial (RFC 3966)

ADR-0003 · 2026-09-20 · Status: accepted
Keywords: post-dial, postd, post dial string, DTMF, IVR, pause, extension vs post-dial, comma semicolon hash, ;ext=, ;postd=, RFC 3966, postDialString, why is my extension a menu path, American trailing hash, #24, #27, ADR-0002, #28

## Context

RFC 3966 (the `tel:` URI) treats an **extension** (`;ext=`) and a **post-dial string** (`;postd=`) as two different parameters, because they are two different things. An extension is a sub-address dialled *after* a call connects; a post-dial string is a sequence sent *while* dialling — a pause, a DTMF run, an IVR menu path. The characters that introduce a post-dial string in ordinary written form are `,` (pause), `;`, `#` and `~` (wait); `+1 212 555 0123,4` almost always means "dial, pause, press 4," not "extension 4."

This port — matching upstream libphonenumber — returns all of those as an **extension**. That gives a caller a value that is real, attached to the right number, and **wrong about what it is**: a DTMF/menu sequence labelled as an extension. Nothing detects it, because the number is correct — the same silent-wrong shape as #22–#26, one concept over. A consumer that tokenizes or displays the extension gets a post-dial sequence in the wrong field.

The deeper reason it is worth fixing rather than tuning: the boundary is **unknowable except by marker convention** — only `;ext=` and `;postd=` are specified; everything else (`x`, `ext`, `,`, `;`, `#`, `poste`) is convention, so any rule is a decision about what the text does not say. #27 (ADR-0002) already made the analogous call for alphabetic markers — the same principle applies to which markers mean *extension* and which mean *post-dial*.

Constraints in force: compat mode is byte-identical to upstream (ADR-0001); `PhoneNumber` is a read-only value object; `formatToE164()` carries neither extension nor post-dial. Raised in downstream review as #28; confirmed against RFC 3966.

## Decision

**Extension and post-dial are separated by marker, and post-dial is exposed as its own field.**

- **A new read-only `PhoneNumber.postDialString: String?`** holds the post-dial sequence. Like `extension`, it is never part of `formatToE164()` and is populated only when a post-dial marker is present.
- **The marker split:**
  - **Extension** (`PhoneNumber.extension`): `;ext=`, the word labels (`ext`, `extn`, `extension`, `exten`, `xtn`, `xt`, `x`, `anexo`, `доб`, `int`, the full-width forms) and any caller-supplied `extensionMarkers`.
  - **Post-dial** (`PhoneNumber.postDialString`): `,` and `,,`, `;`, `#`, `~`, and the American "separators then digits then `#`" form.
- **Default mode only.** `libphonenumberCompat = true` is unchanged and byte-identical to upstream, which returns these as an *extension* — the parity anchor holds.
- **A marker with no digits after it is still dropped** (recorded in `droppedText`, ADR-0002); this decision is only about which *field* a captured value lands in.
- This **supersedes part of #24** in default mode: the American trailing-`#` group becomes `postDialString`, not a re-folded extension. The #24 guarantee that survives is the one that matters — the digits are never folded into the national number.

**Rejected: reclassify only — drop `,`/`;`/`#` without exposing post-dial.** (Option 1 in the #28 discussion.) It removes the wrong-concept extension, but throws away a value a caller may genuinely need to dial. A typed field is only marginally more work and is honest about what the sequence is.

**Rejected: change nothing, keep conflating post-dial with extension.** It is what upstream does, but the default mode already departs from upstream where upstream is silently wrong, and this is that case. With releases infrequent, shipping a known wrong-concept value as the default is the wrong trade.

**Rejected: expose only a generic "matched marker" string, not a typed post-dial field.** It tells a caller *what* matched but still leaves them to interpret it; a dedicated `postDialString` names the concept and pairs with `extension` symmetrically. (The matched-marker idea remains available as a later addition if a need appears.)

**Rejected: invent a boundary rule beyond the marker convention.** The boundary is not derivable from the digits; following RFC 3966's parameter distinction and upstream's marker set is the most defensible line, and keeps compat coherent.

## Consequences

**Easier.** Extension and post-dial are distinct, correctly-named values. A caller can dial the post-dial sequence, ignore it, or reject on it deliberately, instead of finding a DTMF/menu path sitting in the extension field. A token derived from `extension` no longer silently absorbs a post-dial sequence.

**Harder.** Another `PhoneNumber` field, and a marker split that must be maintained as the extension/post-dial vocabularies evolve. The exhaustive-`when` concern does not apply (no new `ErrorType`), but the parse tail gains a branch.

**What changed in output.** Default-mode results move `,`/`;`/`#`/`~` sequences — and the American trailing-`#` group — from `extension` to `postDialString`; this is a called-out default-mode change and revises #24's default. `formatToE164()` is unchanged for every number. Compat output is untouched, so a consumer pinned to compat is unaffected.

**Downstream.** `aughtone-normalize` raised this and is moving toward callers supplying the extension explicitly; a typed `postDialString` gives them a place to route the post-dial sequence rather than refusing or misfiling it.
