/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetector
import org.tensorflow.lite.examples.objectdetection.detectors.TaskVisionDetector
import org.tensorflow.lite.examples.objectdetection.detectors.YoloDetector
import org.tensorflow.lite.examples.objectdetection.tracking.CentroidTracker
import org.tensorflow.lite.examples.objectdetection.tracking.LineCrossingDetector
import org.tensorflow.lite.examples.objectdetection.tracking.TrackedObject
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions


class ObjectDetectorHelper(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 3,
    var currentDelegate: Int = 0,
    var currentModel: Int = 3,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var objectDetector: ObjectDetector? = null

    private var centroidTracker: CentroidTracker? = null
    private var lineCrossingDetector: LineCrossingDetector? = null

    private var isCountingActive = false

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector = null
    }

    fun startCounting(imageWidth: Int, imageHeight: Int) {
        centroidTracker = CentroidTracker(
            maxDisappeared = 30,
            maxDistance = 100f
        )

        val lineX = imageWidth * 0.5f
        lineCrossingDetector = LineCrossingDetector(
            lineStart = Pair(lineX, 0f),
            lineEnd = Pair(lineX, imageHeight.toFloat())
        )

        isCountingActive = true
    }

    fun stopCounting() {
        isCountingActive = false
    }

    fun resetCounting() {
        centroidTracker?.reset()
        lineCrossingDetector?.reset()
    }

    fun isCountingActive(): Boolean = isCountingActive

    fun getCrossingStats(): Map<String, Int> {
        return lineCrossingDetector?.getCrossingStats() ?: mapOf(
            "total" to 0,
            "upward" to 0,
            "downward" to 0
        )
    }

    fun getLineCrossingDetector(): LineCrossingDetector? = lineCrossingDetector

    fun setupObjectDetector() {

        try {

            if (currentModel == MODEL_YOLO || currentModel == MODEL_YOLO_FRUIT) {

                objectDetector = YoloDetector(

                    threshold,
                    0.5f,
                    numThreads,
                    maxResults,
                    currentDelegate,
                    currentModel,
                    context,

                    )

            } else {

                val optionsBuilder =
                    ObjectDetectorOptions.builder()
                        .setScoreThreshold(threshold)
                        .setMaxResults(maxResults)

                val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

                when (currentDelegate) {
                    DELEGATE_CPU -> {
                    }

                    DELEGATE_GPU -> {
//                        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
//                            baseOptionsBuilder.useGpu()
//                        } else {
//                            objectDetectorListener?.onError("GPU is not supported on this device")
//                        }
                        baseOptionsBuilder.useGpu()
                    }

                    DELEGATE_NNAPI -> {
                        baseOptionsBuilder.useNnapi()
                    }
                }

                optionsBuilder.setBaseOptions(baseOptionsBuilder.build())
                val options = optionsBuilder.build()

                objectDetector = TaskVisionDetector(
                    options,
                    currentModel,
                    context,

                    )

            }


        } catch (e: Exception) {

            objectDetectorListener?.onError(e.toString())

        }


    }


    fun detect(image: Bitmap, imageRotation: Int) {

        if (objectDetector == null) {
            setupObjectDetector()
        }

        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        var inferenceTime = SystemClock.uptimeMillis()

        val results = objectDetector?.detect(tensorImage, imageRotation)

        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (results != null) {
            var trackedObjects: List<TrackedObject>? = null
            var newCrossings: List<Int>? = null

            if (isCountingActive && centroidTracker != null && lineCrossingDetector != null) {
                trackedObjects = centroidTracker!!.update(results.detections)

                newCrossings = lineCrossingDetector!!.update(trackedObjects)
            }

            objectDetectorListener?.onResults(
                results.detections,
                inferenceTime,
                results.image.height,
                results.image.width,
                trackedObjects,
                newCrossings
            )
        }

    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: List<ObjectDetection>,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int,
            trackedObjects: List<TrackedObject>? = null,
            newCrossings: List<Int>? = null
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTDETV0 = 1
        const val MODEL_EFFICIENTDETV1 = 2
        const val MODEL_YOLO = 3
        const val MODEL_YOLO_FRUIT = 4
    }
}