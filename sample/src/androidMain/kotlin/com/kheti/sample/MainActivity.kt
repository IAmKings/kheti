package com.kheti.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 开发用：adb shell am start -n com.kheti.sample/.MainActivity --ez vertical true --ez annotation true
        val initialVertical = intent?.getBooleanExtra("vertical", false) ?: false
        val initialAnnotation = intent?.getBooleanExtra("annotation", false) ?: false
        val initialGrid = intent?.getBooleanExtra("grid", false) ?: false
        setContent {
            App(
                initialVertical = initialVertical,
                initialAnnotation = initialAnnotation,
                initialGrid = initialGrid,
            )
        }
    }
}
