package com.looknote.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Luu cong thuc vao mot tep JSON trong bo nho rieng cua ung dung.
 * Khong gioi han so luong; ghi lai toan bo tep moi khi thay doi (an toan qua tep tam).
 */
class RecipeStore(private val file: File) {
    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    @Synchronized
    fun load() {
        val loaded = if (file.exists()) {
            runCatching { RecipeCodec.listFromString(file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
        } else {
            BuiltInRecipes.all.also {
                persist(it)
                runCatching { File(file.parentFile, "builtins.version").writeText(BuiltInRecipes.VERSION.toString()) }
            }
        }
        _recipes.value = sort(loaded)
        upgradeBuiltIns()
    }

    /** Khi bo cong thuc mau duoc cap nhat: thay ban mau cu, them mau moi, giu trang thai yeu thich. */
    private fun upgradeBuiltIns() {
        val vFile = File(file.parentFile, "builtins.version")
        val old = runCatching { vFile.readText().trim().toInt() }.getOrDefault(1)
        if (old >= BuiltInRecipes.VERSION) return
        val favs = _recipes.value.filter { it.builtIn && it.favorite }.map { it.id }.toSet()
        val fresh = BuiltInRecipes.all.map { if (it.id in favs) it.copy(favorite = true) else it }
        val ids = fresh.map { it.id }.toSet()
        commit(_recipes.value.filterNot { it.builtIn && it.id in ids } + fresh)
        runCatching { vFile.writeText(BuiltInRecipes.VERSION.toString()) }
    }

    fun get(id: String): Recipe? = _recipes.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(r: Recipe) {
        val updated = r.copy(updatedAt = System.currentTimeMillis())
        val list = _recipes.value.filterNot { it.id == r.id } + updated
        commit(list)
    }

    @Synchronized
    fun delete(id: String) = commit(_recipes.value.filterNot { it.id == id })

    @Synchronized
    fun toggleFavorite(id: String) {
        commit(_recipes.value.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    @Synchronized
    fun importAll(items: List<Recipe>): Int {
        val existing = _recipes.value.associateBy { it.id }.toMutableMap()
        items.forEach { existing[it.id] = it }
        commit(existing.values.toList())
        return items.size
    }

    @Synchronized
    fun restoreBuiltIns() {
        val ids = BuiltInRecipes.all.map { it.id }.toSet()
        commit(_recipes.value.filterNot { it.id in ids } + BuiltInRecipes.all)
    }

    fun exportAll(): String = RecipeCodec.listToString(_recipes.value)

    private fun commit(list: List<Recipe>) {
        val sorted = sort(list)
        persist(sorted)
        _recipes.value = sorted
    }

    private fun persist(list: List<Recipe>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(RecipeCodec.listToString(list), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun sort(list: List<Recipe>): List<Recipe> =
        list.sortedWith(compareByDescending<Recipe> { it.favorite }
            .thenBy { it.builtIn }
            .thenByDescending { it.updatedAt })
}
