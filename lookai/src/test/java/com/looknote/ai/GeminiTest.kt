package com.looknote.ai

import com.looknote.ai.gemini.GeminiClient
import com.looknote.ai.gemini.GeminiException
import com.looknote.app.model.BuiltInRecipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTest {
    private val recipes = BuiltInRecipes.all.filter { !it.isPP }

    @Test
    fun suggestParsesGenerateContent() {
        val id = recipes[3].id
        val calls = mutableListOf<String>()
        val c = GeminiClient("k") { url, body, key ->
            calls += url
            assertTrue(body.contains("inline_data")); assertEquals("k", key)
            200 to """{"candidates":[{"content":{"parts":[{"text":"{\"recipeId\":\"$id\",\"alternatives\":[\"x\",\"${recipes[0].id}\"],\"skinSmoothing\":140,\"hasPeople\":true,\"reason\":\"Nắng chiều\"}"}]}}]}"""
        }
        val s = c.suggest(byteArrayOf(1, 2, 3), recipes, listOf("m1"))
        assertEquals(id, s.recipeId)
        assertEquals(listOf(recipes[0].id), s.alternatives)
        assertEquals(100, s.skinSmoothing)
        assertTrue(s.hasPeople)
        assertTrue(calls.single().endsWith("/models/m1:generateContent"))
    }

    @Test
    fun fallsBackToNextModelAndInteractions() {
        val id = recipes[1].id
        val calls = mutableListOf<String>()
        val c = GeminiClient("k") { url, body, _ ->
            calls += url
            when {
                url.endsWith("/interactions") && body.contains("\"m1\"") -> 404 to """{"error":{"message":"nf"}}"""
                url.contains("m1") -> 404 to """{"error":{"message":"not found"}}"""
                url.endsWith("/interactions") -> 200 to """{"steps":[{"type":"model_output","content":[{"type":"text","text":"```json\n{\"recipeId\":\"$id\",\"skinSmoothing\":30,\"hasPeople\":true,\"reason\":\"ok\"}\n```"}]}]}"""
                else -> 400 to """{"error":{"message":"bad"}}"""
            }
        }
        val s = c.suggest(byteArrayOf(1), recipes, listOf("m1", "m2"))
        assertEquals(id, s.recipeId)
        assertEquals("m2", s.model)
    }

    @Test
    fun badKeyStops() {
        var n = 0
        val c = GeminiClient("k") { _, _, _ -> n++; 403 to """{"error":{"message":"denied"}}""" }
        try { c.suggest(byteArrayOf(1), recipes, listOf("a", "b")); assertTrue(false) } catch (e: GeminiException) {
            assertTrue(e.message!!.contains("quyền"))
        }
        assertEquals(1, n)
    }

    @Test
    fun retouchFindsImage() {
        val data = java.util.Base64.getEncoder().encodeToString(ByteArray(200) { it.toByte() })
        val c = GeminiClient("k") { url, body, _ ->
            assertTrue(body.contains("IMAGE"))
            200 to """{"candidates":[{"content":{"parts":[{"text":"done"},{"inlineData":{"mimeType":"image/png","data":"$data"}}]}}]}"""
        }
        val (mime, bytes, _) = c.retouch(byteArrayOf(9), listOf("img"))
        assertEquals("image/png", mime)
        assertEquals(200, bytes.size)
    }
}
