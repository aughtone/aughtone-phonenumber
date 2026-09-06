/*
 * Copyright 2026 The Aught One Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.aughtone.phonenumber

/**
 * A small, deterministic backtracking regex matcher for the subset of syntax used
 * by libphonenumber metadata patterns. It exists because `kotlin.text.Regex` is
 * NOT one engine across Kotlin targets: JVM uses java.util.regex, JS uses the host
 * RegExp, and Kotlin/Native + Kotlin/Wasm share a Kotlin implementation that
 * mis-backtracks unequal-length alternation branches (upstream bug KT-89187;
 * related KT-57906). That divergence breaks byte-stable output on Native and
 * Wasm — the exact targets this port exists to support. This matcher is
 * pure commonMain, so every target runs the identical engine and produces
 * identical results by construction.
 *
 * This file is original to this repository (not derived from Google's sources).
 *
 * Supported syntax: literals, '.', character classes `[...]` with ranges and
 * leading-'^' negation, the escapes `\d \D \w \W \s \S` and escaped literals,
 * capturing `( )` and non-capturing `(?: )` groups, alternation `|`, greedy and
 * lazy quantifiers `? * + {n} {n,m} {n,}`, and the `^` / `$` anchors. Not
 * supported (and not used by the metadata): lookaround and backreferences.
 */
internal class PhonePattern(pattern: String) {
    private val root: Node
    private val groupCount: Int

    init {
        val parser = Parser(pattern)
        root = parser.parseAlternation()
        groupCount = parser.groupCount
    }

    /** True if [input] matches the whole pattern (anchored at both ends). */
    fun matches(input: String): Boolean {
        val state = MatchState(input, groupCount)
        return root.match(state, 0) { end -> end == input.length }
    }

    /**
     * Match the pattern anchored at the start of [input] (like a leading `^`),
     * greedily, returning the match (end index + capture groups) or null. Used to
     * strip national prefixes / carrier codes.
     */
    fun matchAtStart(input: String): PhoneMatch? {
        val state = MatchState(input, groupCount)
        var endPos = -1
        val ok = root.match(state, 0) { end -> endPos = end; true }
        if (!ok) return null
        val groups = ArrayList<String?>(groupCount + 1)
        groups.add(input.substring(0, endPos)) // group 0 = whole match
        for (i in 1..groupCount) {
            val s = state.groupStart[i]; val e = state.groupEnd[i]
            groups.add(if (s >= 0 && e >= 0) input.substring(s, e) else null)
        }
        return PhoneMatch(endPos, groups)
    }

    // --- match state -------------------------------------------------------

    private class MatchState(val input: String, groupCount: Int) {
        val groupStart = IntArray(groupCount + 1) { -1 }
        val groupEnd = IntArray(groupCount + 1) { -1 }
    }

    // --- AST ---------------------------------------------------------------
    // Matching uses continuation-passing so backtracking (quantifiers,
    // alternation) is expressed by trying each option and calling `k` (the rest
    // of the pattern); the first branch whose continuation succeeds wins.

    private sealed interface Node {
        fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean
    }

    /** Ordered alternation; tries each branch in turn (this is the backtracking
     *  the platform engines get wrong for unequal-length branches). */
    private class Alt(val branches: List<Node>) : Node {
        override fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean {
            for (b in branches) if (b.match(s, pos, k)) return true
            return false
        }
    }

    /** Concatenation: match each element, threading the continuation. */
    private class Seq(val items: List<Node>) : Node {
        override fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean {
            fun step(i: Int, p: Int): Boolean =
                if (i == items.size) k(p) else items[i].match(s, p) { np -> step(i + 1, np) }
            return step(0, pos)
        }
    }

    private class Group(val body: Node, val index: Int) : Node {
        override fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean {
            if (index == 0) return body.match(s, pos, k) // non-capturing
            val savedStart = s.groupStart[index]; val savedEnd = s.groupEnd[index]
            val ok = body.match(s, pos) { end ->
                s.groupStart[index] = pos; s.groupEnd[index] = end
                if (k(end)) true else { s.groupStart[index] = savedStart; s.groupEnd[index] = savedEnd; false }
            }
            if (!ok) { s.groupStart[index] = savedStart; s.groupEnd[index] = savedEnd }
            return ok
        }
    }

    /** Single-character matcher (literal, '.', class, or escape). */
    private class Single(val test: (Char) -> Boolean) : Node {
        override fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean =
            pos < s.input.length && test(s.input[pos]) && k(pos + 1)
    }

    private class Anchor(val end: Boolean) : Node {
        override fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean =
            if ((end && pos == s.input.length) || (!end && pos == 0)) k(pos) else false
    }

    /** Quantifier over [child]: greedy tries max repetitions first, lazy tries min. */
    private class Repeat(val child: Node, val min: Int, val max: Int, val greedy: Boolean) : Node {
        override fun match(s: MatchState, pos: Int, k: (Int) -> Boolean): Boolean {
            fun rep(count: Int, p: Int): Boolean {
                val canMore = count < max
                if (greedy) {
                    if (canMore && child.match(s, p) { np -> if (np == p && count >= min) false else rep(count + 1, np) }) return true
                    return count >= min && k(p)
                } else {
                    if (count >= min && k(p)) return true
                    return canMore && child.match(s, p) { np -> if (np == p) false else rep(count + 1, np) }
                }
            }
            return rep(0, pos)
        }
    }

    // --- Parser ------------------------------------------------------------

    private class Parser(private val src: String) {
        private var i = 0

        /** Number of capturing groups seen so far; final total after parsing. */
        var groupCount = 0
            private set

        fun parseAlternation(): Node {
            val branches = mutableListOf(parseSequence())
            while (peek() == '|') { i++; branches.add(parseSequence()) }
            return if (branches.size == 1) branches[0] else Alt(branches)
        }

        private fun parseSequence(): Node {
            val items = mutableListOf<Node>()
            while (i < src.length && peek() != '|' && peek() != ')') {
                items.add(parseQuantified())
            }
            return if (items.size == 1) items[0] else Seq(items)
        }

        private fun parseQuantified(): Node {
            val atom = parseAtom()
            val c = peek()
            val (min, max) = when (c) {
                '?' -> { i++; 0 to 1 }
                '*' -> { i++; 0 to Int.MAX_VALUE }
                '+' -> { i++; 1 to Int.MAX_VALUE }
                '{' -> parseBraces()
                else -> return atom
            }
            var greedy = true
            if (peek() == '?') { i++; greedy = false } // lazy
            return Repeat(atom, min, max, greedy)
        }

        private fun parseBraces(): Pair<Int, Int> {
            i++ // consume '{'
            val start = i
            while (peek() != '}' && peek() != ',') i++
            val min = src.substring(start, i).toInt()
            var max = min
            if (peek() == ',') {
                i++
                val ms = i
                while (peek() != '}') i++
                max = if (i == ms) Int.MAX_VALUE else src.substring(ms, i).toInt()
            }
            i++ // consume '}'
            return min to max
        }

        private fun parseAtom(): Node = when (peek()) {
            '(' -> parseGroup()
            '[' -> parseClass()
            '.' -> { i++; Single { true } }
            '^' -> { i++; Anchor(end = false) }
            '$' -> { i++; Anchor(end = true) }
            '\\' -> parseEscape()
            else -> { val ch = src[i++]; Single { it == ch } }
        }

        private fun parseGroup(): Node {
            i++ // '('
            val capturing = !(peek() == '?' && i + 1 < src.length && src[i + 1] == ':')
            if (!capturing) i += 2 // skip '?:'
            val index = if (capturing) ++groupCount else 0
            val body = parseAlternation()
            require(peek() == ')') { "Unclosed group in pattern: $src" }
            i++ // ')'
            return Group(body, index)
        }

        private fun parseEscape(): Node {
            i++ // '\'
            val c = src[i++]
            return when (c) {
                'd' -> Single { it in '0'..'9' }
                'D' -> Single { it !in '0'..'9' }
                'w' -> Single { it == '_' || it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }
                'W' -> Single { !(it == '_' || it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z') }
                's' -> Single { it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '\u000B' || it == '\u000C' }
                'S' -> Single { !(it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '\u000B' || it == '\u000C') }
                else -> Single { it == c } // escaped literal, e.g. \. \+ \\
            }
        }

        private fun parseClass(): Node {
            i++ // '['
            val negate = peek() == '^'
            if (negate) i++
            val ranges = mutableListOf<Pair<Char, Char>>()
            val singles = mutableListOf<(Char) -> Boolean>()
            while (peek() != ']') {
                var lo = src[i++]
                if (lo == '\\') {
                    val e = src[i++]
                    when (e) {
                        'd' -> { singles.add { it in '0'..'9' }; continue }
                        'w' -> { singles.add { it == '_' || it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }; continue }
                        's' -> { singles.add { it == ' ' || it == '\t' || it == '\n' || it == '\r' }; continue }
                        else -> lo = e // escaped literal inside class
                    }
                }
                if (peek() == '-' && i + 1 < src.length && src[i + 1] != ']') {
                    i++ // '-'
                    val hi = src[i++]
                    ranges.add(lo to hi)
                } else {
                    val ch = lo
                    singles.add { it == ch }
                }
            }
            i++ // ']'
            return Single { c ->
                val hit = ranges.any { (lo, hi) -> c in lo..hi } || singles.any { it(c) }
                if (negate) !hit else hit
            }
        }

        private fun peek(): Char = if (i < src.length) src[i] else ' '
    }
}

/** Result of [PhonePattern.matchAtStart]: [end] is the index after the match; [groups] is group 0..n. */
internal class PhoneMatch(val end: Int, val groups: List<String?>)
