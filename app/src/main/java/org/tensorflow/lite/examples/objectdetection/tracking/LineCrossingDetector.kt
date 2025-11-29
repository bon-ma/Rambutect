package org.tensorflow.lite.examples.objectdetection.tracking

class LineCrossingDetector(
    private var lineStart: Pair<Float, Float>,
    private var lineEnd: Pair<Float, Float>
) {
    private var totalCrossings = 0

    private var ripeCrossings = 0
    private var unripeCrossings = 0

    private val recentlyCrossed = mutableMapOf<Int, Long>()
    private val crossingCooldown = 1000L // 1 second cooldown

    private val lastSideOfLine = mutableMapOf<Int, Boolean>()

    fun update(trackedObjects: List<TrackedObject>): List<Int> {
        val newCrossings = mutableListOf<Int>()
        val currentTime = System.currentTimeMillis()

        val keysToRemove = recentlyCrossed.filter { (_, time) ->
            currentTime - time > crossingCooldown
        }.keys
        keysToRemove.forEach { recentlyCrossed.remove(it) }

        for (obj in trackedObjects) {
            if (recentlyCrossed.containsKey(obj.id)) {
                continue
            }

            if (obj.positionHistory.size < 2) {
                lastSideOfLine[obj.id] = isAboveLine(obj.centroid)
                continue
            }

            val previousPos = obj.positionHistory[obj.positionHistory.size - 2]
            val currentPos = obj.centroid

            if (doSegmentsIntersect(previousPos, currentPos, lineStart, lineEnd)) {
                val wasAbove = lastSideOfLine[obj.id] ?: isAboveLine(previousPos)
                val isAbove = isAboveLine(currentPos)

                if (wasAbove != isAbove) {
                    totalCrossings++

                    val label = obj.detection.category.label.lowercase()
                    when {
                        label.contains("ripe") && !label.contains("unripe") -> ripeCrossings++
                        label.contains("unripe") -> unripeCrossings++
                    }

                    newCrossings.add(obj.id)
                    recentlyCrossed[obj.id] = currentTime
                }
            }

            lastSideOfLine[obj.id] = isAboveLine(currentPos)
        }

        val trackedIds = trackedObjects.map { it.id }.toSet()
        val obsoleteIds = lastSideOfLine.keys.filter { it !in trackedIds }
        obsoleteIds.forEach { lastSideOfLine.remove(it) }

        return newCrossings
    }

    private fun doSegmentsIntersect(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>,
        p4: Pair<Float, Float>
    ): Boolean {
        val d1 = direction(p3, p4, p1)
        val d2 = direction(p3, p4, p2)
        val d3 = direction(p1, p2, p3)
        val d4 = direction(p1, p2, p4)

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        ) {
            return true
        }

        if (d1 == 0f && onSegment(p3, p1, p4)) return true
        if (d2 == 0f && onSegment(p3, p2, p4)) return true
        if (d3 == 0f && onSegment(p1, p3, p2)) return true
        if (d4 == 0f && onSegment(p1, p4, p2)) return true

        return false
    }

    private fun direction(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>
    ): Float {
        return (p3.first - p1.first) * (p2.second - p1.second) -
                (p2.first - p1.first) * (p3.second - p1.second)
    }

    private fun onSegment(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>
    ): Boolean {
        return p2.first <= maxOf(p1.first, p3.first) &&
                p2.first >= minOf(p1.first, p3.first) &&
                p2.second <= maxOf(p1.second, p3.second) &&
                p2.second >= minOf(p1.second, p3.second)
    }

    private fun isAboveLine(point: Pair<Float, Float>): Boolean {
        val d = direction(lineStart, lineEnd, point)
        return d > 0
    }

    fun getTotalCrossings(): Int = totalCrossings

    fun getRipeCrossings(): Int = ripeCrossings

    fun getUnripeCrossings(): Int = unripeCrossings

    fun getCrossingStats(): Map<String, Int> {
        return mapOf(
            "total" to totalCrossings,
            "ripe" to ripeCrossings,
            "unripe" to unripeCrossings
        )
    }

    fun setLine(start: Pair<Float, Float>, end: Pair<Float, Float>) {
        lineStart = start
        lineEnd = end
    }

    fun getLineStart(): Pair<Float, Float> = lineStart

    fun getLineEnd(): Pair<Float, Float> = lineEnd

    fun reset() {
        totalCrossings = 0
        ripeCrossings = 0
        unripeCrossings = 0
        recentlyCrossed.clear()
        lastSideOfLine.clear()
    }
}