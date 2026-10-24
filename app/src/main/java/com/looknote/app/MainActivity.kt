package com.looknote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.looknote.app.ui.LookNoteRoot
import com.looknote.app.ui.theme.LookNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        enableEdgeToEdge()
        setContent {
            LookNoteTheme {
                LookNoteRoot()
            }
        }
    }
}
