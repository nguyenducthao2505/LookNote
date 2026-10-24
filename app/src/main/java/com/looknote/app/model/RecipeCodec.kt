package com.looknote.app.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.UUID

/** Chuyen doi Recipe <-> JSON va ma chia se (dung trong ma QR). */
object RecipeCodec {
    const val SHARE_PREFIX = "LOOKNOTE1:"

    private val json = Json { ignoreUnknownKeys = true }

    fun toJson(r: Recipe, full: Boolean = true): JsonObject = buildJsonObject {
        if (full) {
            put("id", r.id)
            put("fav", r.favorite)
            put("builtIn", r.builtIn)
            put("created", r.createdAt)
            put("updated", r.updatedAt)
        }
        put("n", r.name)
        put("look", r.look.code)
        put("p", buildJsonObject {
            LookParam.entries.forEach { put(it.key, r.value(it)) }
        })
        put("wb", r.wb.key)
        if (r.wb == WhiteBalance.KELVIN) put("k", r.kelvin)
        if (r.wbAB != 0.0) put("ab", r.wbAB)
        if (r.wbGM != 0.0) put("gm", r.wbGM)
        if (r.dro.isNotBlank()) put("dro", r.dro)
        if (r.exposureComp.isNotBlank()) put("ev", r.exposureComp)
        if (r.camera.isNotBlank()) put("cam", r.camera)
        if (r.notes.isNotBlank()) put("note", r.notes)
    }

    fun fromJson(o: JsonObject, keepId: Boolean = true): Recipe {
        fun s(k: String): String? = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        val params = mutableMapOf<LookParam, Int>()
        (o["p"] as? JsonObject)?.forEach { (k, v) ->
            val p = LookParam.fromKey(k)
            val iv = v.jsonPrimitive.intOrNull
            if (p != null && iv != null) params[p] = iv.coerceIn(p.min, p.max)
        }
        val now = System.currentTimeMillis()
        return Recipe(
            id = if (keepId) s("id") ?: UUID.randomUUID().toString() else UUID.randomUUID().toString(),
            name = s("n") ?: "",
            look = CreativeLook.fromCode(s("look")) ?: CreativeLook.ST,
            params = params,
            wb = WhiteBalance.fromKey(s("wb")),
            kelvin = o["k"]?.jsonPrimitive?.intOrNull ?: 5500,
            wbAB = WbShift.snap(o["ab"]?.jsonPrimitive?.doubleOrNull ?: 0.0, WbShift.STEP_AB),
            wbGM = WbShift.snap(o["gm"]?.jsonPrimitive?.doubleOrNull ?: 0.0, WbShift.STEP_GM),
            dro = s("dro") ?: "",
            exposureComp = s("ev") ?: "",
            camera = s("cam") ?: "",
            notes = s("note") ?: "",
            favorite = keepId && (o["fav"]?.jsonPrimitive?.booleanOrNull ?: false),
            builtIn = keepId && (o["builtIn"]?.jsonPrimitive?.booleanOrNull ?: false),
            createdAt = if (keepId) o["created"]?.jsonPrimitive?.longOrNull ?: now else now,
            updatedAt = if (keepId) o["updated"]?.jsonPrimitive?.longOrNull ?: now else now,
        )
    }

    fun listToString(list: List<Recipe>): String {
        val arr: JsonArray = buildJsonArray { list.forEach { add(toJson(it)) } }
        return arr.toString()
    }

    fun listFromString(text: String): List<Recipe> {
        val el: JsonElement = json.parseToJsonElement(text)
        return el.jsonArray.mapNotNull { runCatching { fromJson(it.jsonObject) }.getOrNull() }
    }

    /** Ma chia se gon: tien to + base64url(JSON khong co id). */
    fun toShareCode(r: Recipe): String {
        val compact = toJson(r, full = false).toString()
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(compact.toByteArray(Charsets.UTF_8))
        return SHARE_PREFIX + b64
    }

    /** Doc ma chia se hoac JSON thuan. Tra ve null neu khong hop le. */
    fun fromShareCode(text: String): Recipe? {
        val t = text.trim()
        return runCatching {
            val body = when {
                t.startsWith(SHARE_PREFIX) ->
                    String(Base64.getUrlDecoder().decode(t.removePrefix(SHARE_PREFIX).trim()), Charsets.UTF_8)
                t.startsWith("{") -> t
                else -> return null
            }
            fromJson(json.parseToJsonElement(body).jsonObject, keepId = false)
        }.getOrNull()
    }
}
