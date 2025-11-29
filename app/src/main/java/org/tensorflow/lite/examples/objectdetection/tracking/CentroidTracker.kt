package org.tensorflow.lite.examples.objectdetection.tracking

import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import kotlin.math.pow
import kotlin.math.sqrt

class CentroidTracker(
    private val maxDisappeared: Int = 30,
    private val maxDistance: Float = 100f
) {
    private val trackedObjects = mutableMapOf<Int, TrackedObject>()

    private val disappeared = mutableMapOf<Int, Int>()

    private var nextObjectId = 0

    fun update(detections: List<ObjectDetection>): List<TrackedObject> {
        if (detections.isEmpty()) {
            val keysToRemove = mutableListOf<Int>()

            for (objectId in disappeared.keys) {
                disappeared[objectId] = disappeared[objectId]!! + 1

                if (disappeared[objectId]!! > maxDisappeared) {
                    keysToRemove.add(objectId)
                }
            }

            keysToRemove.forEach { id ->
                trackedObjects.remove(id)
                disappeared.remove(id)
            }

            return trackedObjects.values.toList()
        }

        val inputCentroids = detections.map { detection ->
            TrackedObject.calculateCentroid(detection.boundingBox)
        }

        if (trackedObjects.isEmpty()) {
            detections.forEachIndexed { index, detection ->
                register(detection)
            }
        } else {
            val objectIds = trackedObjects.keys.toList()
            val objectCentroids = trackedObjects.values.map { it.centroid }

            val distances = computeDistanceMatrix(objectCentroids, inputCentroids)

            val (usedRows, usedCols) = matchObjects(distances, objectIds, detections)

            val unusedRows = objectIds.indices.filter { it !in usedRows }
            for (row in unusedRows) {
                val objectId = objectIds[row]
                disappeared[objectId] = disappeared[objectId]!! + 1

                if (disappeared[objectId]!! > maxDisappeared) {
                    trackedObjects.remove(objectId)
                    disappeared.remove(objectId)
                }
            }

            val unusedCols = detections.indices.filter { it !in usedCols }
            for (col in unusedCols) {
                register(detections[col])
            }
        }

        return trackedObjects.values.toList()
    }

    private fun register(detection: ObjectDetection) {
        val trackedObject = TrackedObject.fromDetection(nextObjectId, detection)
        trackedObjects[nextObjectId] = trackedObject
        disappeared[nextObjectId] = 0
        nextObjectId++
    }

    private fun computeDistanceMatrix(
        centroids1: List<Pair<Float, Float>>,
        centroids2: List<Pair<Float, Float>>
    ): Array<FloatArray> {
        val distances = Array(centroids1.size) { FloatArray(centroids2.size) }

        for (i in centroids1.indices) {
            for (j in centroids2.indices) {
                distances[i][j] = euclideanDistance(centroids1[i], centroids2[j])
            }
        }

        return distances
    }

    private fun euclideanDistance(p1: Pair<Float, Float>, p2: Pair<Float, Float>): Float {
        val dx = p1.first - p2.first
        val dy = p1.second - p2.second
        return sqrt(dx.pow(2) + dy.pow(2))
    }

    private fun matchObjects(
        distances: Array<FloatArray>,
        objectIds: List<Int>,
        detections: List<ObjectDetection>
    ): Pair<Set<Int>, Set<Int>> {
        val usedRows = mutableSetOf<Int>()
        val usedCols = mutableSetOf<Int>()

        val flatDistances = mutableListOf<Triple<Int, Int, Float>>()
        for (i in distances.indices) {
            for (j in distances[i].indices) {
                flatDistances.add(Triple(i, j, distances[i][j]))
            }
        }

        flatDistances.sortBy { it.third }

        for ((row, col, distance) in flatDistances) {
            if (row in usedRows || col in usedCols || distance > maxDistance) {
                continue
            }

            val objectId = objectIds[row]
            val detection = detections[col]

            trackedObjects[objectId] = trackedObjects[objectId]!!.update(detection)
            disappeared[objectId] = 0

            usedRows.add(row)
            usedCols.add(col)
        }

        return Pair(usedRows, usedCols)
    }

    fun reset() {
        trackedObjects.clear()
        disappeared.clear()
        nextObjectId = 0
    }

    fun getTrackedObjects(): List<TrackedObject> {
        return trackedObjects.values.toList()
    }
}