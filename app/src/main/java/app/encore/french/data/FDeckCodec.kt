package app.encore.french.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object Fingerprints {
    fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    fun card(front: String, back: String): String {
        val canonical = "${normalize(front)}\u0000${normalize(back)}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

object FDeckCodec {
    fun parse(json: String): FDeck {
        val root = try { JSONObject(json) } catch (_: JSONException) {
            throw DeckParseException.Invalid("This file is not valid JSON.")
        }
        if (root.optString("format") != "fdeck") throw DeckParseException.Invalid("Not an fdeck file: format must be \"fdeck\".")
        if (!root.has("version")) throw DeckParseException.Invalid("Missing required field: version.")
        val version = root.optInt("version", -1)
        if (version != 1) throw DeckParseException.UnsupportedVersion(version)
        val name = requiredString(root, "name", "deck").trim()
        val array = root.optJSONArray("cards") ?: throw DeckParseException.Invalid("Missing required array: cards.")
        val cards = buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: throw DeckParseException.Invalid("Card ${index + 1} must be an object.")
                val front = requiredString(item, "front", "card ${index + 1}").trim()
                val back = requiredString(item, "back", "card ${index + 1}").trim()
                val gender = optionalString(item, "gender")?.lowercase(Locale.ROOT)
                if (gender != null && gender !in setOf("m", "f")) {
                    throw DeckParseException.Invalid("Card ${index + 1} has invalid gender; use \"m\" or \"f\".")
                }
                val tags = item.optJSONArray("tags")?.toStrings(index) ?: emptyList()
                add(ImportCard(front, back, gender, optionalString(item, "example"),
                    optionalString(item, "exampleTranslation"), optionalString(item, "note"), tags))
            }
        }
        return FDeck(name, cards)
    }

    fun encode(name: String, cards: List<CardEntity>): String = JSONObject().apply {
        put("format", "fdeck")
        put("version", 1)
        put("name", name)
        put("cards", JSONArray().apply { cards.forEach { card -> put(JSONObject().apply {
            put("front", card.front); put("back", card.back)
            card.gender?.let { put("gender", it) }
            card.example?.let { put("example", it) }
            card.exampleTranslation?.let { put("exampleTranslation", it) }
            card.note?.let { put("note", it) }
            if (card.tags.isNotBlank()) put("tags", JSONArray(card.tags.split(TAG_SEPARATOR)))
        }) } })
    }.toString(2)

    private fun requiredString(obj: JSONObject, key: String, context: String): String {
        if (!obj.has(key) || obj.isNull(key) || obj.optString(key).isBlank())
            throw DeckParseException.Invalid("Missing or empty $key in $context.")
        return obj.optString(key)
    }

    private fun optionalString(obj: JSONObject, key: String): String? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONArray.toStrings(cardIndex: Int): List<String> = buildList {
        repeat(length()) { i ->
            val value = opt(i)
            if (value !is String) throw DeckParseException.Invalid("Tag ${i + 1} on card ${cardIndex + 1} must be text.")
            value.trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }
}

const val TAG_SEPARATOR = "\u001F"
