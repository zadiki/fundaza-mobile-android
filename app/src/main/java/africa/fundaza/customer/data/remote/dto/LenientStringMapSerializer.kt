package africa.fundaza.customer.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a free-form `{"key": value}` object (beneficiary `fields`) as `Map<String, String>`.
 *
 * The server sends `null` for fields a beneficiary never filled in, and may send numbers or
 * booleans. `coerceInputValues` does not reach map values, so the stock serializer would fail
 * the whole response. Here nulls (and nested objects/arrays) are dropped and other primitives
 * are kept as their text.
 */
object LenientStringMapSerializer : KSerializer<Map<String, String>> {
    private val strict = MapSerializer(String.serializer(), String.serializer())

    override val descriptor = strict.descriptor

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val json = decoder as? JsonDecoder ?: return strict.deserialize(decoder)
        val obj = json.decodeJsonElement() as? JsonObject ?: return emptyMap()
        return buildMap {
            obj.forEach { (key, value) ->
                if (value is JsonPrimitive && value !is JsonNull) put(key, value.content)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, String>) =
        strict.serialize(encoder, value)
}
