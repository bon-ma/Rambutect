package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.tensorflow.lite.examples.objectdetection.MainActivity
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.examples.objectdetection.adapters.GalleryAdapter
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import org.tensorflow.lite.examples.objectdetection.tracking.TrackedObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var galleryAdapter: GalleryAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var currentDetections: List<ObjectDetection> = emptyList()
    private var currentImageWidth: Int = 0
    private var currentImageHeight: Int = 0

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                fragmentCameraBinding.loadingOverlay.visibility = View.VISIBLE

                cameraExecutor.execute {
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(
                                requireActivity().contentResolver,
                                uri
                            )
                            android.graphics.ImageDecoder.decodeBitmap(source)
                        } else {
                            MediaStore.Images.Media.getBitmap(
                                requireActivity().contentResolver,
                                uri
                            )
                        }.copy(Bitmap.Config.ARGB_8888, true)

                        activity?.runOnUiThread {
                            (activity as? MainActivity)?.isImageDetection = true
                            (activity as? MainActivity)?.uploadedBitmap = bitmap

                            fragmentCameraBinding.imagePreview.setImageBitmap(bitmap)
                            fragmentCameraBinding.imagePreview.visibility = View.VISIBLE
                            fragmentCameraBinding.viewFinder.visibility = View.GONE

                            objectDetectorHelper.detect(bitmap, 0)
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            fragmentCameraBinding.loadingOverlay.visibility = View.GONE
                            Toast.makeText(
                                requireContext(),
                                "Error loading image: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this
        )

        cameraExecutor = Executors.newSingleThreadExecutor()

        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        initBottomSheetControls()
        setupBottomSheet()
        setupGallery()
        setupCountingControls()

        fragmentCameraBinding.buttonUpload.setOnClickListener {
            // Disabled for demonstration
            Toast.makeText(
                requireContext(),
                "Upload mode disabled for demonstration",
                Toast.LENGTH_SHORT
            ).show()
        }

        fragmentCameraBinding.buttonCapture.setOnClickListener {
            if (!::bitmapBuffer.isInitialized) {
                Toast.makeText(requireContext(), "No image available to save", Toast.LENGTH_SHORT)
                    .show()
            } else {
                saveImage(bitmapBuffer)
            }
        }

        fragmentCameraBinding.startButton.setOnClickListener {
            fragmentCameraBinding.cameraMenu.visibility = View.GONE
            fragmentCameraBinding.bottomSheetMenu.root.visibility = View.GONE
        }

        fragmentCameraBinding.buttonMenu.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        fragmentCameraBinding.apply {
            buttonCaptureMode.setOnClickListener {
                buttonCapture.visibility = View.VISIBLE
                buttonUpload.visibility = View.VISIBLE
                buttonStartCounting.visibility = View.GONE
                buttonResetCounting.visibility = View.GONE

                if (objectDetectorHelper.isCountingActive()) {
                    stopCounting()
                }
            }

            buttonRealtimeMode.setOnClickListener {
                buttonCapture.visibility = View.GONE
                buttonUpload.visibility = View.GONE
                buttonStartCounting.visibility = View.GONE
                buttonResetCounting.visibility = View.GONE

                if (objectDetectorHelper.isCountingActive()) {
                    stopCounting()
                }
            }

            buttonScanMode.setOnClickListener {
                buttonCapture.visibility = View.GONE
                buttonUpload.visibility = View.GONE
                buttonStartCounting.visibility = View.VISIBLE
                buttonResetCounting.visibility = View.GONE
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(fragmentCameraBinding.bottomSheetLayout.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupCountingControls() {
        fragmentCameraBinding.buttonStartCounting.setOnClickListener {
            if (objectDetectorHelper.isCountingActive()) {
                stopCounting()
            } else {
                startCounting()
            }
        }

        fragmentCameraBinding.buttonResetCounting.setOnClickListener {
            objectDetectorHelper.resetCounting()
            Toast.makeText(requireContext(), "Counting reset", Toast.LENGTH_SHORT).show()
            updateOverlay()
        }

        fragmentCameraBinding.buttonStartCounting.visibility = View.GONE
        fragmentCameraBinding.buttonResetCounting.visibility = View.GONE
    }

    private fun startCounting() {
        if (currentImageWidth == 0 || currentImageHeight == 0) {
            Toast.makeText(
                requireContext(),
                "Waiting for camera initialization...",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        objectDetectorHelper.startCounting(currentImageWidth, currentImageHeight)

        fragmentCameraBinding.buttonStartCounting.setImageResource(android.R.drawable.ic_media_pause)
        fragmentCameraBinding.buttonStartCounting.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light)

        fragmentCameraBinding.buttonResetCounting.visibility = View.VISIBLE

        Toast.makeText(requireContext(), "Counting started", Toast.LENGTH_SHORT).show()
    }

    private fun stopCounting() {
        objectDetectorHelper.stopCounting()

        objectDetectorHelper.resetCounting()

        fragmentCameraBinding.buttonStartCounting.setImageResource(android.R.drawable.ic_media_play)
        fragmentCameraBinding.buttonStartCounting.backgroundTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF6200EE"))

        fragmentCameraBinding.buttonResetCounting.visibility = View.GONE

        fragmentCameraBinding.overlay.clear()

        Toast.makeText(requireContext(), "Counting stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateOverlay() {
        activity?.runOnUiThread {
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    private fun setupGallery() {
        val galleryDir = File(requireContext().getExternalFilesDir(null), "detections")
        if (!galleryDir.exists()) {
            galleryDir.mkdirs()
        }
        val imageList = galleryDir.listFiles()?.toMutableList() ?: mutableListOf()
        galleryAdapter = GalleryAdapter(imageList)
        val menuBinding = fragmentCameraBinding.bottomSheetMenu
        menuBinding.galleryRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = galleryAdapter
        }
        menuBinding.emptyGalleryText.visibility =
            if (imageList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateGallery() {
        val galleryDir = File(requireContext().getExternalFilesDir(null), "detections")
        val imageList = galleryDir.listFiles()?.toList() ?: emptyList()
        galleryAdapter.updateData(imageList)
        val menuBinding = fragmentCameraBinding.bottomSheetMenu
        menuBinding.emptyGalleryText.visibility =
            if (imageList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveImage(bitmap: Bitmap) {
        val galleryDir = File(requireContext().getExternalFilesDir(null), "detections")
        if (!galleryDir.exists()) galleryDir.mkdirs()

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val boxPaint = Paint().apply {
            color = ContextCompat.getColor(requireContext(), R.color.bounding_box_color)
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }

        val textBackgroundPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
            textSize = 48f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            textSize = 48f
            isAntiAlias = true
        }

        val bounds = android.graphics.Rect()

        for (detection in currentDetections) {
            val box = detection.boundingBox

            canvas.drawRect(box, boxPaint)

            val label = "${detection.category.label} ${
                String.format(
                    "%.2f",
                    detection.category.confidence
                )
            }"
            textBackgroundPaint.getTextBounds(label, 0, label.length, bounds)

            val textWidth = bounds.width()
            val textHeight = bounds.height()

            canvas.drawRect(
                box.left,
                box.top,
                box.left + textWidth + 16f,
                box.top + textHeight + 16f,
                textBackgroundPaint
            )

            canvas.drawText(label, box.left + 8f, box.top + textHeight + 8f, textPaint)
        }

        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(galleryDir, "IMG_$timeStamp.jpg")
        try {
            val fos = FileOutputStream(imageFile)
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
            Toast.makeText(
                requireContext(),
                "Image saved with ${currentDetections.size} detections",
                Toast.LENGTH_SHORT
            ).show()
            updateGallery()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error saving image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(
        results: List<ObjectDetection>,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int,
        trackedObjects: List<TrackedObject>?,
        newCrossings: List<Int>?
    ) {
        currentDetections = results
        currentImageWidth = imageWidth
        currentImageHeight = imageHeight

        activity?.runOnUiThread {
            fragmentCameraBinding.loadingOverlay.visibility = View.GONE

            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", inferenceTime)

            fragmentCameraBinding.overlay.setResults(
                detectionResults = results ?: emptyList(),
                imageHeight = imageHeight,
                imageWidth = imageWidth,
                isStatic = false,
                trackedObjs = trackedObjects,
                crossings = newCrossings,
                crossingDetector = objectDetectorHelper.getLineCrossingDetector(),
                countingActive = objectDetectorHelper.isCountingActive(),
                crossingStats = objectDetectorHelper.getCrossingStats()
            )

            fragmentCameraBinding.overlay.invalidate()
        }
    }

    private fun initBottomSheetControls() {
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentModel = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", objectDetectorHelper.threshold)

        objectDetectorHelper.clearObjectDetector()
        fragmentCameraBinding.overlay.clear()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()

                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }

                        detectObjects(image)
                    }
                }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            preview?.surfaceProvider = fragmentCameraBinding.viewFinder.surfaceProvider
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}