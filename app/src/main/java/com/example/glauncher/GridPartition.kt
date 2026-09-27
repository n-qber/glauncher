package com.example.glauncher

import java.text.Normalizer
import java.util.Locale

data class GridBucket(
    val apps: List<AppInfo>,
    val rangeLabel: String,
    val countText: String = "${apps.size} apps",
    val isSingleApp: Boolean = (apps.size == 1)
)

object GridPartition {

    fun partition(candidates: List<AppInfo>): List<GridBucket?> {
        if (candidates.isEmpty()) {
            return List(6) { null }
        }

        // If 6 or fewer apps, each gets its own circle with full icon
        if (candidates.size <= 6) {
            val buckets = mutableListOf<GridBucket?>()
            for (app in candidates) {
                buckets.add(
                    GridBucket(
                        apps = listOf(app),
                        rangeLabel = app.label,
                        countText = "",
                        isSingleApp = true
                    )
                )
            }
            while (buckets.size < 6) {
                buckets.add(null)
            }
            return buckets
        }

        // Find common prefix among all candidates (e.g. "Mercado ")
        val commonPrefixLen = findCommonPrefixLength(candidates.map { normalize(it.label) })

        // Partition candidates into 6 contiguous slices
        val total = candidates.size
        val baseSize = total / 6
        val remainder = total % 6

        val slices = mutableListOf<List<AppInfo>>()
        var startIndex = 0
        for (i in 0 until 6) {
            val size = baseSize + if (i < remainder) 1 else 0
            if (size > 0 && startIndex < total) {
                val endIndex = minOf(startIndex + size, total)
                slices.add(candidates.subList(startIndex, endIndex))
                startIndex = endIndex
            }
        }

        val buckets = slices.map { slice ->
            if (slice.size == 1) {
                GridBucket(
                    apps = slice,
                    rangeLabel = slice.first().label,
                    countText = "",
                    isSingleApp = true
                )
            } else {
                val label = computeRangeLabel(slice, commonPrefixLen)
                GridBucket(
                    apps = slice,
                    rangeLabel = label,
                    countText = "${slice.size} apps",
                    isSingleApp = false
                )
            }
        }.toMutableList<GridBucket?>()

        while (buckets.size < 6) {
            buckets.add(null)
        }

        return buckets
    }

    fun findCommonPrefixLength(labels: List<String>): Int {
        if (labels.isEmpty()) return 0
        val first = labels[0]
        var i = 0
        while (i < first.length) {
            val ch = first[i]
            if (labels.any { i >= it.length || !it[i].equals(ch, ignoreCase = true) }) {
                break
            }
            i++
        }
        return i
    }

    private fun computeRangeLabel(slice: List<AppInfo>, globalPrefixLen: Int): String {
        val firstNorm = normalize(slice.first().label)
        val lastNorm = normalize(slice.last().label)

        // Drop the common global prefix
        val firstRem = firstNorm.drop(globalPrefixLen).trimStart()
        val lastRem = lastNorm.drop(globalPrefixLen).trimStart()

        if (firstRem.isEmpty() || lastRem.isEmpty()) {
            val c1 = firstNorm.firstOrNull()?.uppercaseChar() ?: '?'
            val c2 = lastNorm.firstOrNull()?.uppercaseChar() ?: '?'
            return if (c1 == c2) "$c1" else "$c1 – $c2"
        }

        val c1 = firstRem.first()
        val c2 = lastRem.first()

        if (!c1.equals(c2, ignoreCase = true)) {
            // First letters differ: e.g. "A – E"
            return "${c1.uppercaseChar()} – ${c2.uppercaseChar()}"
        }

        // Both start with the same letter/prefix in this bucket
        // Find the first index where they differ
        val localShared = findCommonPrefixLength(listOf(firstRem, lastRem))
        val sub1 = firstRem.substring(0, minOf(localShared + 1, firstRem.length)).trim().uppercase(Locale.getDefault())
        val sub2 = lastRem.substring(0, minOf(localShared + 1, lastRem.length)).trim().uppercase(Locale.getDefault())

        return if (sub1 == sub2) {
            sub1
        } else {
            "$sub1 – $sub2"
        }
    }

    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").trim()
    }
}
