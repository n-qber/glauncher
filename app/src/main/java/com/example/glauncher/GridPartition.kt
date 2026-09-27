package com.example.glauncher

import java.util.Locale

data class GridBucket(
    val apps: List<AppInfo>,
    val rangeLabel: String,
    val countText: String = "${apps.size} apps",
    val isSingleApp: Boolean = (apps.size == 1),
    val nextCharIndex: Int = 0
)

object GridPartition {

    fun partition(candidates: List<AppInfo>, currentCharIndex: Int = 0): List<GridBucket?> {
        if (candidates.isEmpty()) {
            return List(6) { null }
        }

        // If candidates <= 6, each app directly gets its own circle with app icon
        if (candidates.size <= 6) {
            val buckets = mutableListOf<GridBucket?>()
            for (app in candidates) {
                buckets.add(
                    GridBucket(
                        apps = listOf(app),
                        rangeLabel = app.label,
                        countText = "",
                        isSingleApp = true,
                        nextCharIndex = currentCharIndex
                    )
                )
            }
            while (buckets.size < 6) {
                buckets.add(null)
            }
            return buckets
        }

        // Advance activeIndex while ALL candidates share the same character at activeIndex (e.g. "Mercado ")
        var activeIndex = currentCharIndex
        while (true) {
            val firstChar = candidates[0].getChar(activeIndex)
            if (firstChar != null && candidates.all { it.getChar(activeIndex) == firstChar }) {
                activeIndex++
            } else {
                break
            }
        }

        // Group candidate apps by their character at activeIndex
        val charToApps = linkedMapOf<Char, MutableList<AppInfo>>()
        for (app in candidates) {
            val ch = app.getChar(activeIndex) ?: ' '
            charToApps.getOrPut(ch) { mutableListOf() }.add(app)
        }

        val distinctChars = charToApps.keys.toList().sorted()

        val buckets = mutableListOf<GridBucket?>()

        if (distinctChars.size <= 6) {
            // Up to 6 distinct characters: each character gets its own bucket
            for (ch in distinctChars) {
                val appsForChar = charToApps[ch] ?: emptyList()
                if (appsForChar.size == 1) {
                    val app = appsForChar.first()
                    buckets.add(
                        GridBucket(
                            apps = appsForChar,
                            rangeLabel = app.label,
                            countText = "",
                            isSingleApp = true,
                            nextCharIndex = activeIndex + 1
                        )
                    )
                } else {
                    buckets.add(
                        GridBucket(
                            apps = appsForChar,
                            rangeLabel = ch.toString().uppercase(Locale.getDefault()),
                            countText = "${appsForChar.size} apps",
                            isSingleApp = false,
                            nextCharIndex = activeIndex + 1
                        )
                    )
                }
            }
        } else {
            // More than 6 distinct characters: partition distinct characters into 6 contiguous ranges
            val totalChars = distinctChars.size
            val baseSize = totalChars / 6
            val remainder = totalChars % 6

            var charStartIdx = 0
            for (i in 0 until 6) {
                val sliceSize = baseSize + if (i < remainder) 1 else 0
                if (sliceSize > 0 && charStartIdx < totalChars) {
                    val charEndIdx = minOf(charStartIdx + sliceSize, totalChars)
                    val sliceChars = distinctChars.subList(charStartIdx, charEndIdx)
                    val cStart = sliceChars.first()
                    val cEnd = sliceChars.last()

                    val appsInSlice = sliceChars.flatMap { charToApps[it] ?: emptyList() }

                    if (appsInSlice.size == 1) {
                        val app = appsInSlice.first()
                        buckets.add(
                            GridBucket(
                                apps = appsInSlice,
                                rangeLabel = app.label,
                                countText = "",
                                isSingleApp = true,
                                nextCharIndex = activeIndex + 1
                            )
                        )
                    } else {
                        val label = if (cStart == cEnd) {
                            cStart.toString()
                        } else {
                            "$cStart – $cEnd"
                        }
                        buckets.add(
                            GridBucket(
                                apps = appsInSlice,
                                rangeLabel = label,
                                countText = "${appsInSlice.size} apps",
                                isSingleApp = false,
                                nextCharIndex = activeIndex + 1
                            )
                        )
                    }
                    charStartIdx = charEndIdx
                }
            }
        }

        while (buckets.size < 6) {
            buckets.add(null)
        }

        return buckets
    }
}
