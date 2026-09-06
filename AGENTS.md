# Agent Onboarding Guide

Welcome, AI Contributor. You are expected to operate at the level of a Senior Staff Engineer focusing on clean architecture, DX, and exact compliance with existing standards.

# Working in this repository

## This repository is public

Everything committed here is world-readable and permanent — a public git
history cannot be unpublished, only added to. Write for a stranger who
found this repo, not for the maintainer.

**Never commit personal details.** Concretely, that means no real names in
prose or code comments, no personal email addresses, no machine or
hostnames, no absolute home paths, no employer, client or private project
names, no internal tracker keys or instance URLs, and nothing about the
maintainer's business, billing, tax position or working habits. Verbatim
quotes from a working conversation are the most common way all of this
leaks at once — a design discussion is full of them.

The exceptions are deliberate and narrow: the copyright line in `LICENSE`
and `NOTICE`, maintainer attribution in `README.md`, the GitHub handle and
its `users.noreply.github.com` address where a vendor field needs one, and
the published Maven coordinates used as worked examples. Do not add to that
list without being asked, and do not strip what is on it.

Authorship of the tool is fine; identity beyond it is not. A GitHub handle
is the right granularity — it is already public, and it is the name the
project is known by.

**Document the pattern, not the person.** When a design came out of
someone's specific situation — how they work, what tools they pay for, what
their client expects — the reusable content is the *pattern*: this is a
common way developers work, here is why the obvious design fails against
it, here is how this one covers it. Written that way the reasoning survives
intact and nothing traces back to an individual. If a fact only makes sense
as "the maintainer does X", it does not belong here.

This applies hardest to documents an agent generates from a conversation —
RADs, ADRs, design notes, session summaries. Those are written while the
conversation is still in context, which is exactly when quoting feels
natural and is most dangerous.

**Examples use placeholders.** `acme`, `example.com`, `PROJ-123`,
`<instance>.youtrack.cloud`, `owner/repo`. Never a real project, org or
instance, even one that happens to be public — a real name in an example
reads as a live reference and invites someone to go look.

**Check before you commit.** Grep your own additions for names, emails,
hosts, home paths and project names before proposing them. If something is
borderline, leave it out and say so — it is far cheaper to add a detail
later than to remove one from a public history.


## Core Principles
- **Test-Driven Development (TDD)**: Ensure tests are written for all common and edge cases before logic modification.
- **Immutability-First**: Default to `val`, `data class`, and `value class`. Avoid mutable state.
- **Strict Compliance**: This is a direct port of a reference library (Google's libphonenumber). Avoid unnecessary abstraction or deviation from the reference behaviour — parsing, formatting, and validation must match the reference so that output stays byte-stable within a metadata epoch.

## Interaction Rules
- **Plan-First**: Any architectural or logic change requires a formal Implementation Plan and explicit user approval before modifying code.
- **Minimal Changes**: Avoid formatting or refactoring files that are outside the scope of the immediate task.

## Repository Skills
Do not add `*.ai-skill.md`, `META-INF/ai-skills/` or `META-INF/agents/skills/` to this repository, and do not scan dependencies for them.

## Apache 2.0 attribution in source headers

This library is a derivative work of Google's libphonenumber (Apache 2.0). The `LICENSE` and `NOTICE` files at the repository root are complete — do not change them without being asked.

Every derived source file must carry three things:

1. **§4(c) — the upstream copyright notice, retained verbatim.** Copy the exact copyright line from the corresponding upstream Java file. libphonenumber's headers read `Copyright (C) <year> The Libphonenumber Authors`, and the year varies per file (2009–2014). Keep that line first and unchanged.
2. **The port's own copyright line.** `Copyright 2026 The Aught One Authors`.
3. **§4(b) — a modification notice.** These files were ported from Java to Kotlin Multiplatform, which is a modification, so the header must say so.

The header for each derived source file — keep the upstream line first, add to it, and do not remove or reword anything already there:

```kotlin
/*
 * Copyright (C) 2009 The Libphonenumber Authors
 * Copyright 2026 The Aught One Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ... leave the existing licence body exactly as it is ...
 *
 * Modifications: ported from the original Java implementation to Kotlin
 * Multiplatform. Structure and behaviour follow the original; the implementation
 * was rewritten in Kotlin.
 */
```

Google's upstream repository's own implementations are C++, Java and JavaScript; there is no Kotlin implementation upstream. This port is therefore derived from the Java implementation, and the retained `The Libphonenumber Authors` headers are correct.

Files **not** derived from Google's work (anything original to this repository) carry only `Copyright 2026 The Aught One Authors` and no modification notice — over-attributing is as misleading as under-attributing, because it obscures who wrote what.

## Publishing outside Google's repository

libphonenumber does not take language ports into its main repository; community ports live in their own repositories, maintained by their authors. This port is published independently and is not affiliated with or endorsed by Google. If the port is ever listed anywhere upstream, that is the maintainer's call — do not open or prepare a pull request against `google/libphonenumber` unless asked.

## The metadata epoch (byte-stability)

This port embeds a pinned slice of libphonenumber metadata and treats its version as a normalization **epoch**. Output (canonical E.164) is frozen within a released version so downstream consumers can derive stable tokens from it. The pinned version lives in `gradle/libs.versions.toml` as `libphonenumberMetadata` and is exposed at runtime. A metadata bump is a NEW epoch shipped as a new library version — never an in-place change. Do not change parsing/formatting output within a version.
