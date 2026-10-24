package com.looknote.app

import android.content.Context
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeStore
import com.looknote.app.usb.CameraController
import java.io.File

/** Noi giu cac doi tuong dung chung trong toan ung dung. */
object AppGraph {
    lateinit var store: RecipeStore
        private set
    lateinit var camera: CameraController
        private set

    /** Ban nhap cong thuc truyen tu man hinh khac sang man hinh soan. */
    var pendingDraft: Recipe? = null

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        store = RecipeStore(File(app.filesDir, "recipes.json")).also { it.load() }
        camera = CameraController(app)
        initialized = true
    }
}
