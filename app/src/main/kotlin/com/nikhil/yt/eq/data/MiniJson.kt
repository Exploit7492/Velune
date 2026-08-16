package com.nikhil.yt.eq.data

/**
 * A minimal, dependency-free JSON parser. Not a general-purpose JSON
 * library — just enough (objects, arrays, strings with standard escapes,
 * numbers, booleans, null) to read GitHub's REST API responses in
 * [PresetIrRepository] without adding a new dependency (org.json, Gson,
 * kotlinx.serialization...) for a single call site. Same reasoning as
 * [ImpulseResponseLoader]'s hand-rolled WAV parser: small, intentional,
 * easy to unit-test in isolation.
 */
sealed class JsonValue {
    data class JsonString(val value: String) : JsonValue()
    data class JsonNumber(val value: Double) : JsonValue()
    data class JsonBool(val value: Boolean) : JsonValue()
    object JsonNull : JsonValue()
    data class JsonArray(val items: List<JsonValue>) : JsonValue()
    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue()

    fun asObject(): Map<String, JsonValue> = (this as? JsonObject)?.entries
        ?: throw MiniJsonException("Expected a JSON object, got $this")

    fun asArray(): List<JsonValue> = (this as? JsonArray)?.items
        ?: throw MiniJsonException("Expected a JSON array, got $this")
}

class MiniJsonException(message: String) : Exception(message)

/** Reads [this] as a plain string, or null if absent/JSON-null. */
fun Map<String, JsonValue>.stringOrNull(key: String): String? =
    when (val v = this[key]) {
        is JsonValue.JsonString -> v.value
        else -> null
    }

fun Map<String, JsonValue>.string(key: String): String =
    stringOrNull(key) ?: throw MiniJsonException("Missing required string field \"$key\"")

fun Map<String, JsonValue>.numberOrNull(key: String): Double? =
    when (val v = this[key]) {
        is JsonValue.JsonNumber -> v.value
        else -> null
    }

object MiniJson {

    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw MiniJsonException("Trailing content after JSON value at ${parser.pos}")
        return value
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun atEnd() = pos >= text.length
        private fun peek(): Char {
            if (atEnd()) throw MiniJsonException("Unexpected end of input")
            return text[pos]
        }
        private fun advance(): Char = peek().also { pos++ }

        fun skipWhitespace() {
            while (!atEnd() && text[pos].isWhitespace()) pos++
        }

        private fun expect(c: Char) {
            if (atEnd() || text[pos] != c) {
                throw MiniJsonException("Expected '$c' at position $pos, got ${if (atEnd()) "<eof>" else text[pos]}")
            }
            pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            return when {
                atEnd() -> throw MiniJsonException("Unexpected end of input")
                peek() == '{' -> parseObject()
                peek() == '[' -> parseArray()
                peek() == '"' -> JsonValue.JsonString(parseStringLiteral())
                peek() == 't' -> { expectLiteral("true"); JsonValue.JsonBool(true) }
                peek() == 'f' -> { expectLiteral("false"); JsonValue.JsonBool(false) }
                peek() == 'n' -> { expectLiteral("null"); JsonValue.JsonNull }
                else -> parseNumber()
            }
        }

        private fun expectLiteral(literal: String) {
            if (pos + literal.length > text.length || text.substring(pos, pos + literal.length) != literal) {
                throw MiniJsonException("Expected literal \"$literal\" at position $pos")
            }
            pos += literal.length
        }

        private fun parseObject(): JsonValue.JsonObject {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (!atEnd() && peek() == '}') { pos++; return JsonValue.JsonObject(entries) }
            while (true) {
                skipWhitespace()
                val key = parseStringLiteral()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                entries[key] = value
                skipWhitespace()
                when {
                    !atEnd() && peek() == ',' -> { pos++; continue }
                    !atEnd() && peek() == '}' -> { pos++; break }
                    else -> throw MiniJsonException("Expected ',' or '}' in object at position $pos")
                }
            }
            return JsonValue.JsonObject(entries)
        }

        private fun parseArray(): JsonValue.JsonArray {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (!atEnd() && peek() == ']') { pos++; return JsonValue.JsonArray(items) }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when {
                    !atEnd() && peek() == ',' -> { pos++; continue }
                    !atEnd() && peek() == ']' -> { pos++; break }
                    else -> throw MiniJsonException("Expected ',' or ']' in array at position $pos")
                }
            }
            return JsonValue.JsonArray(items)
        }

        private fun parseStringLiteral(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw MiniJsonException("Unterminated string")
                val c = advance()
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw MiniJsonException("Unterminated escape sequence")
                        when (val esc = advance()) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw MiniJsonException("Truncated \\u escape")
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw MiniJsonException("Unknown escape sequence \\$esc")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): JsonValue.JsonNumber {
            val start = pos
            if (!atEnd() && peek() == '-') pos++
            while (!atEnd() && text[pos].isDigit()) pos++
            if (!atEnd() && text[pos] == '.') {
                pos++
                while (!atEnd() && text[pos].isDigit()) pos++
            }
            if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                while (!atEnd() && text[pos].isDigit()) pos++
            }
            if (pos == start) throw MiniJsonException("Invalid number at position $pos")
            return JsonValue.JsonNumber(text.substring(start, pos).toDouble())
        }
    }
}
