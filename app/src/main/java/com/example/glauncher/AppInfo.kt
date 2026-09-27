package com.example.glauncher

import android.graphics.drawable.Drawable
import java.text.Normalizer

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
) {
    val cleanLabel: String = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .trim()

    fun getChar(index: Int): Char? {
        if (index < 0 || index >= cleanLabel.length) return null
        return cleanLabel[index].uppercaseChar()
    }
}
