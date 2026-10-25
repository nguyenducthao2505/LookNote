package com.looknote.ai

import android.content.Context
import com.looknote.ai.gemini.GeminiClient

/** Cai dat luu tren may. API key chi luu trong bo nho rieng cua app. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lookai", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    var textModels: String
        get() = sp.getString("textModels", null) ?: GeminiClient.DEFAULT_TEXT_MODELS.joinToString(", ")
        set(v) = sp.edit().putString("textModels", v).apply()

    var imageModels: String
        get() = sp.getString("imageModels", null) ?: GeminiClient.DEFAULT_IMAGE_MODELS.joinToString(", ")
        set(v) = sp.edit().putString("imageModels", v).apply()

    /** Canh dai toi da khi xuat anh (0 = giu nguyen). */
    var exportMaxEdge: Int
        get() = sp.getInt("exportMaxEdge", 4096)
        set(v) = sp.edit().putInt("exportMaxEdge", v).apply()

    var jpegQuality: Int
        get() = sp.getInt("jpegQuality", 95)
        set(v) = sp.edit().putInt("jpegQuality", v.coerceIn(70, 100)).apply()

    fun textModelList() = textModels.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun imageModelList() = imageModels.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}
