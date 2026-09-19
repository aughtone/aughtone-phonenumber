# Aught One – libphonenumber

The project's durable knowledge: what this port covers and deliberately does not, why the E.164 output is shaped the way it is, and how to work on it. Anything with a status that will someday be "done" is a [tracker issue](https://github.com/aughtone/aughtone-phonenumber/issues) instead.

- **[Architecture Decision Records](decisions/README.md)** — hard-to-reverse choices, and why the alternatives lost.
- **[Research](research/README.md)** — investigations and designs still being worked out.
- **[Reference](reference/README.md)** — settled facts kept close, including the [upstream test coverage matrix](reference/upstream-test-coverage.md).

The governing constraint runs through all of it: **the same input must produce the same canonical E.164 bytes, on any platform, within a released version.** A consumer derives a stable token from the canonical form and discards the original, so a one-byte difference is an undetectable, unrecoverable miss.

What this library is, and is not, is settled in [ADR-0001](decisions/ADR-0001-scope-of-the-libphonenumber-port.md): a port of libphonenumber's number **parsing, validation, and formatting**, and not its as-you-type formatter, number matcher, or short-number subsystem.
