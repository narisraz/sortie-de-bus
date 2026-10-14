package com.narisraz.sortiedebus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("sortie-de-bus", MODE_PRIVATE)
        setContent {
            GameScreen(
                startLevel = prefs.getInt("level", 1).coerceAtLeast(1),
                onLevel = { prefs.edit().putInt("level", it).apply() },
            )
        }
    }
}
