package br.com.nqber.glauncher

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()

data class AppInfo(
    val label: String,
    val packageName: String,
    val cleanLabel: String = normalizeLabel(label)
) {
    fun getChar(index: Int): Char? {
        if (index < 0 || index >= cleanLabel.length) return null
        return cleanLabel[index]
    }

    companion object {
        fun normalizeLabel(label: String): String {
            return Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                .replace(DIACRITICS_REGEX, "")
                .trim()
                .uppercase(Locale.getDefault())
        }
    }
}

