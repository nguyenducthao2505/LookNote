package com.looknote.ai.gemini

import com.looknote.app.model.Recipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class GeminiException(message: String, val httpCode: Int = 0) : Exception(message)

/** Ket qua goi y cua Gemini. */
data class Suggestion(
    val recipeId: String,
    val alternatives: List<String>,
    val skinSmoothing: Int,
    val hasPeople: Boolean,
    val reason: String,
    val model: String,
)

/**
 * Goi Gemini API qua REST (khong can thu vien Google).
 * - suggest(): model doc anh (mien phi) chon cong thuc + muc lam min da.
 * - retouch(): model tra ve anh (tra phi) chi lam min da.
 */
class GeminiClient(
    private val apiKey: String,
    private val http: (url: String, body: String, key: String) -> Pair<Int, String> = ::post,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"

        val DEFAULT_TEXT_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.1-flash-lite", "gemini-3-flash-preview")
        val DEFAULT_IMAGE_MODELS = listOf("gemini-3.1-flash-lite-image", "gemini-nano-banana-2.1", "gemini-3.1-flash-image", "gemini-2.5-flash-image")

        const val RETOUCH_PROMPT =
            "Retouch this photograph by smoothing the skin of every person naturally, like a professional beauty retoucher. " +
                "Only reduce blemishes, acne, uneven texture and harsh pores while keeping realistic skin texture. " +
                "Do NOT change anything else: keep the exact same face shape, facial features, eyes, makeup, expression, hair, " +
                "body, clothes, background, framing, crop, aspect ratio, lighting, colors and white balance. " +
                "Do not add, remove or move any object. Output only the edited photo."

        fun post(url: String, body: String, key: String): Pair<Int, String> {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 180_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", key)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                return code to text
            } catch (e: IOException) {
                throw GeminiException("Không kết nối được Gemini: ${e.message ?: "lỗi mạng"}")
            } finally {
                conn.disconnect()
            }
        }

        /** Tim moi doan van ban trong JSON tra ve (ho tro ca generateContent va Interactions). */
        fun collectText(el: JsonElement, out: MutableList<String> = mutableListOf()): List<String> {
            when (el) {
                is JsonObject -> {
                    val thought = (el["thought"] as? JsonPrimitive)?.booleanOrNull == true
                    val t = el["text"]
                    if (!thought && t is JsonPrimitive && t.isString) out.add(t.content)
                    el.forEach { (k, v) -> if (k != "text") collectText(v, out) }
                }
                is JsonArray -> el.forEach { collectText(it, out) }
                else -> {}
            }
            return out
        }

        /** Tim anh (base64) trong JSON tra ve. Tra ve anh cuoi cung. */
        fun findImage(el: JsonElement): Pair<String, String>? {
            var found: Pair<String, String>? = null
            fun walk(e: JsonElement) {
                when (e) {
                    is JsonObject -> {
                        val inline = (e["inlineData"] ?: e["inline_data"]) as? JsonObject
                        if (inline != null) {
                            val mime = (inline["mimeType"] ?: inline["mime_type"])?.jsonPrimitive?.contentOrNull ?: ""
                            val data = inline["data"]?.jsonPrimitive?.contentOrNull
                            if (data != null && mime.startsWith("image/")) found = mime to data
                        }
                        val type = (e["type"] as? JsonPrimitive)?.contentOrNull
                        val data = (e["data"] as? JsonPrimitive)?.contentOrNull
                        val mime = ((e["mime_type"] ?: e["mimeType"]) as? JsonPrimitive)?.contentOrNull ?: "image/png"
                        if (type == "image" && data != null && data.length > 100) found = mime to data
                        e.forEach { (k, v) -> if (k != "inlineData" && k != "inline_data") walk(v) }
                    }
                    is JsonArray -> e.forEach { walk(it) }
                    else -> {}
                }
            }
            walk(el)
            return found
        }

        fun errorMessage(code: Int, body: String): String {
            val msg = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val hint = when (code) {
                400 -> if (msg?.contains("API key", true) == true) "API key không hợp lệ." else "Yêu cầu không hợp lệ."
                401, 403 -> "API key không có quyền hoặc chưa bật Gemini API."
                404 -> "Không tìm thấy model."
                429 -> "Hết hạn mức (quá nhiều yêu cầu hoặc model này cần bật thanh toán)."
                in 500..599 -> "Máy chủ Gemini đang lỗi, thử lại sau."
                else -> "Lỗi HTTP $code."
            }
            return if (msg.isNullOrBlank()) hint else "$hint ($msg)"
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    /** Goi generateContent; neu model khong ho tro thi thu Interactions API. */
    private fun call(model: String, parts: JsonArray, generationConfig: JsonObject?, interactionsInput: JsonArray, responseFormat: JsonObject?): JsonElement {
        val gcBody = buildJsonObject {
            putJsonArray("contents") { addJsonObject { put("role", "user"); put("parts", parts) } }
            if (generationConfig != null) put("generationConfig", generationConfig)
        }.toString()
        val (code, text) = http("$BASE/models/$model:generateContent", gcBody, apiKey)
        if (code in 200..299) return json.parseToJsonElement(text)
        // Mot so model moi chi phuc vu qua Interactions API
        if (code == 400 || code == 404) {
            val ib = buildJsonObject {
                put("model", model)
                put("input", interactionsInput)
                if (responseFormat != null) put("response_format", responseFormat)
            }.toString()
            val (c2, t2) = http("$BASE/interactions", ib, apiKey)
            if (c2 in 200..299) return json.parseToJsonElement(t2)
            if (c2 != 404) throw GeminiException(errorMessage(c2, t2), c2)
        }
        throw GeminiException(errorMessage(code, text), code)
    }

    private fun <T> tryModels(models: List<String>, block: (String) -> T): T {
        var last: GeminiException? = null
        for (m in models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            try {
                return block(m)
            } catch (e: GeminiException) {
                last = e
                // Loi khoa/quyen: khong thu model khac
                if (e.httpCode == 401 || e.httpCode == 403) throw e
                if (e.httpCode == 400 && e.message?.contains("API key", true) == true) throw e
            }
        }
        throw last ?: GeminiException("Chưa cấu hình model")
    }

    fun buildCatalog(recipes: List<Recipe>): String = recipes.joinToString("\n") { r ->
        val note = r.notes.replace("\n", " ").take(80)
        "${r.id} | ${r.name} | ${r.summary()} | WB ${r.wbText()}" + if (note.isNotBlank()) " | $note" else ""
    }

    /**
     * Gui anh (JPEG da thu nho) va danh sach cong thuc, nhan lai cong thuc phu hop nhat.
     */
    fun suggest(jpeg: ByteArray, recipes: List<Recipe>, models: List<String> = DEFAULT_TEXT_MODELS): Suggestion {
        require(recipes.isNotEmpty())
        val prompt = """
            Bạn là chuyên gia màu ảnh cho máy ảnh Sony (Creative Look).
            Hãy xem ảnh và chọn MỘT công thức màu phù hợp nhất trong danh sách dưới đây
            (cân nhắc chủ thể, ánh sáng, thời điểm, tông màu sẵn có, cảm xúc của cảnh).
            Chọn thêm tối đa 3 công thức thay thế. Chỉ dùng mã id có trong danh sách.
            Nếu ảnh có người (thấy rõ da mặt), đề xuất mức làm mịn da 0–100 (thường 20–50, ảnh cận mặt có mụn/da sần có thể cao hơn); không có người thì 0.
            Lý do viết bằng tiếng Việt, tối đa 2 câu.

            Danh sách (id | tên | thông số | cân bằng trắng | ghi chú):
            ${buildCatalog(recipes)}
        """.trimIndent()
        val schema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("recipeId") { put("type", "STRING") }
                putJsonObject("alternatives") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
                putJsonObject("skinSmoothing") { put("type", "INTEGER") }
                putJsonObject("hasPeople") { put("type", "BOOLEAN") }
                putJsonObject("reason") { put("type", "STRING") }
            }
            putJsonArray("required") { add("recipeId"); add("skinSmoothing"); add("hasPeople"); add("reason") }
        }
        val img = b64(jpeg)
        val parts = buildJsonArray {
            addJsonObject { putJsonObject("inline_data") { put("mime_type", "image/jpeg"); put("data", img) } }
            addJsonObject { put("text", prompt) }
        }
        val gc = buildJsonObject {
            put("responseMimeType", "application/json")
            put("responseSchema", schema)
            put("temperature", 0.3)
        }
        val input = buildJsonArray {
            addJsonObject { put("type", "image"); put("mime_type", "image/jpeg"); put("data", img) }
            addJsonObject { put("type", "text"); put("text", prompt + "\n\nTrả lời DUY NHẤT bằng JSON: {\"recipeId\":\"...\",\"alternatives\":[\"...\"],\"skinSmoothing\":0,\"hasPeople\":false,\"reason\":\"...\"}") }
        }
        val ids = recipes.map { it.id }.toSet()
        return tryModels(models) { model ->
            val resp = call(model, parts, gc, input, null)
            val text = collectText(resp).joinToString("").trim()
            val obj = parseJsonObject(text) ?: throw GeminiException("Gemini trả lời không đúng định dạng")
            val id = obj["recipeId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val chosen = if (id in ids) id else recipes.firstOrNull { it.name.equals(id, true) }?.id
                ?: throw GeminiException("Gemini chọn công thức không có trong danh sách ($id)")
            Suggestion(
                recipeId = chosen,
                alternatives = (obj["alternatives"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it in ids && it != chosen }?.distinct()?.take(3) ?: emptyList(),
                skinSmoothing = (obj["skinSmoothing"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 100),
                hasPeople = obj["hasPeople"]?.jsonPrimitive?.booleanOrNull ?: false,
                reason = obj["reason"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                model = model,
            )
        }
    }

    private fun parseJsonObject(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    /** Lam min da bang model tao anh. Tra ve (mime, bytes). */
    fun retouch(jpeg: ByteArray, models: List<String> = DEFAULT_IMAGE_MODELS): Triple<String, ByteArray, String> {
        val img = b64(jpeg)
        val parts = buildJsonArray {
            addJsonObject { put("text", RETOUCH_PROMPT) }
            addJsonObject { putJsonObject("inline_data") { put("mime_type", "image/jpeg"); put("data", img) } }
        }
        val gc = buildJsonObject {
            putJsonArray("responseModalities") { add("IMAGE") }
        }
        val input = buildJsonArray {
            addJsonObject { put("type", "text"); put("text", RETOUCH_PROMPT) }
            addJsonObject { put("type", "image"); put("mime_type", "image/jpeg"); put("data", img) }
        }
        val rf = buildJsonObject { put("type", "image"); put("mime_type", "image/jpeg"); put("image_size", "2K") }
        return tryModels(models) { model ->
            val resp = call(model, parts, gc, input, rf)
            val found = findImage(resp)
                ?: throw GeminiException("Gemini không trả về ảnh" + collectText(resp).joinToString(" ").take(160).let { if (it.isBlank()) "" else ": $it" })
            Triple(found.first, Base64.getDecoder().decode(found.second), model)
        }
    }
}
