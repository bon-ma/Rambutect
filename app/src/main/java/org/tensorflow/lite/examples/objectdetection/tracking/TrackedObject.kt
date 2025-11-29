package org.tensorflow.lite.examples.objectdetection.tracking

import android.graphics.RectF
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection

data class TrackedObject(
    val id: Int,
    val detection: ObjectDetection,
    val centroid: Pair<Float, Float>,
    val timestamp: Long = System.currentTimeMillis()
) {
    val positionHistory: MutableList<Pair<Float, Float>> = mutableListOf(centroid)

    companion object {
        fun fromDetection(id: Int, detection: ObjectDetection): TrackedObject {
            val centroid = calculateCentroid(detection.boundingBox)
            return TrackedObject(id, detection, centroid)
        }

        fun calculateCentroid(box: RectF): Pair<Float, Float> {
            val centerX = (box.left + box.right) / 2f
            val centerY = (box.top + box.bottom) / 2f
            return Pair(centerX, centerY)
        }
    }

    fun update(detection: ObjectDetection): TrackedObject {
        val newCentroid = calculateCentroid(detection.boundingBox)
        val updated = TrackedObject(id, detection, newCentroid, System.currentTimeMillis())

        updated.positionHistory.addAll(this.positionHistory)
        updated.positionHistory.add(newCentroid)

        if (updated.positionHistory.size > 30) {
            updated.positionHistory.removeAt(0)
        }

        return updated
    }
}