/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sangakuki.camerax.barcode.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.android.camera.utils.YuvToRgbConverter
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.sangakuki.camerax.barcode.R
import com.sangakuki.camerax.barcode.extensions.aspectRatio
import com.sangakuki.camerax.barcode.extensions.cropRect
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

/** Helper type alias used for analysis use case callbacks */
typealias BarCodeListener = (fps: Double?, text: String?, bitmap: Bitmap?) -> Unit

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment() {
    private val TAG = "CameraXBasic"
    private lateinit var container: ConstraintLayout
    private lateinit var previewView: PreviewView
    private lateinit var fpsView: TextView //FPS帧率View
    private lateinit var barCodeView: TextView //二维码View
    private lateinit var imageView: ImageView //帧数据处理预览View

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_camera, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout
        previewView = container.findViewById(R.id.preview_view)
        imageView = container.findViewById(R.id.image_view)
        fpsView = container.findViewById(R.id.tv_fps)
        barCodeView = container.findViewById(R.id.tv_barCode)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Wait for the views to be properly laid out
        previewView.post {

            // Keep track of the display in which this view is attached
            displayId = previewView.display.displayId

            // Bind use cases
            bindCameraUseCases()
        }

    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { previewView.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = previewView.display.rotation

        // Bind the CameraProvider to the LifeCycleOwner
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation).build()

            // ImageAnalysis
            imageAnalyzer = ImageAnalysis.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation).build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor, BarCodeAnalyzer(requireContext()) { fps, code, image ->
                        activity?.runOnUiThread {
                            fps?.let { f -> fpsView.text = DecimalFormat("FPS:#.00").format(f) }
                            code?.let { c -> barCodeView.text = String.format("BarCode: %s", c) }
                            image?.let { i -> imageView.setImageBitmap(i) }
                        }
                    })
                }

            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                // Attach the viewfinder's surface provider to preview use case
                preview?.setSurfaceProvider(previewView.createSurfaceProvider(camera?.cameraInfo))
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }


    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    private class BarCodeAnalyzer(context: Context, listener: BarCodeListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<BarCodeListener>().apply { listener?.let { add(it) } }
        private var framesPerSecond: Double = -1.0
        private val bitmapConfig = Bitmap.Config.ARGB_8888

        private val converter = YuvToRgbConverter(context)
        private val rotateMatrix = Matrix()
        private lateinit var sourceBuffer: IntBuffer
        private val reader: MultiFormatReader = MultiFormatReader().apply {
            val map: EnumMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
            map[DecodeHintType.TRY_HARDER] = BarcodeFormat.QR_CODE
            map[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODABAR)
            map[DecodeHintType.CHARACTER_SET] = "utf-8"
            setHints(map)
        }

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: BarCodeListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        override fun analyze(image: ImageProxy) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) / frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0
            listeners.forEach { it(framesPerSecond, null, null) }

            decodeByYUVData(image)

            image.close()
        }

        /**
         * 直接将YUV数据交给zxing解码
         * 这种方式理论上是最快的，先裁剪后旋转待日后优化
         */
        private fun decodeByYUVData(image: ImageProxy) {
            try {
                val degrees = image.imageInfo.rotationDegrees//旋转角度 一般后置90度，前置270度
                var cropRect = image.cropRect
                var finderRect = cropRect.cropRect()//取景框Rect
                var yuvBuffer = image.planes[0].buffer.toByteArray()//解码时只需要Y通道
                if (degrees == 90) {
                    yuvBuffer = converter.rotateClockwise90(yuvBuffer, cropRect.width(), cropRect.height())
                    cropRect = Rect(0, 0, cropRect.height(), cropRect.width())
                    finderRect = cropRect.cropRect()
                } else if (degrees == 180) {
                    yuvBuffer = converter.rotateClockwise180(yuvBuffer, cropRect.width(), cropRect.height())
                } else if (degrees == 270) {
                    yuvBuffer = converter.rotateClockwise270(yuvBuffer, cropRect.width(), cropRect.height())
                    cropRect = Rect(0, 0, cropRect.height(), cropRect.width())
                    finderRect = cropRect.cropRect()
                }

                val source = PlanarYUVLuminanceSource(yuvBuffer, cropRect.width(), cropRect.height(), finderRect.left, finderRect.top, finderRect.width(), finderRect.height(), false)
                //val thumbnail = Bitmap.createBitmap(source.renderThumbnail(), source.thumbnailWidth, source.thumbnailHeight, bitmapConfig)
                //listeners.forEach { it(null, null, thumbnail) }//一个测试方法回显处理后的Bitmap

                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
                listeners.forEach { it(null, result.text, null) }
            } catch (e: Exception) {
            }
        }

        /***
         * 使用RenderScript将YUV转为Bitmap，裁剪与旋转后交给zxing解码
         * 由于存在yuv与Bitmap转换，降低了一定帧率
         */
        @Suppress("unused")
        private fun decodeByBitmap(image: ImageProxy) {
            var sourceBitmap: Bitmap? = null//YUV转Bitmap
            var finderBitmap: Bitmap? = null//取景框Bitmap
            try {
                val degrees = image.imageInfo.rotationDegrees.toFloat()//旋转角度 一般后置90度，前置270度
                val cropRect = image.cropRect//原始帧Rect
                val finderRect = cropRect.cropRect()//取景框Rect
                sourceBitmap = Bitmap.createBitmap(cropRect.width(), cropRect.height(), bitmapConfig)
                converter.yuvToRgb(image, sourceBitmap)//使用RenderScript将YUV转为Bitmap

                rotateMatrix.reset()
                rotateMatrix.setRotate(degrees)//旋转Bitmap
                finderBitmap = Bitmap.createBitmap(sourceBitmap, finderRect.left, finderRect.top, finderRect.width(), finderRect.height(), rotateMatrix, false)//裁剪与旋转
                //listeners.forEach { it(null, null, finderBitmap?.copy(bitmapConfig, true)) }//一个测试方法回显处理后的Bitmap

                if (!::sourceBuffer.isInitialized) {
                    sourceBuffer = IntBuffer.allocate(finderBitmap.allocationByteCount)
                }
                sourceBuffer.rewind()
                finderBitmap.copyPixelsToBuffer(sourceBuffer)

                val source = RGBLuminanceSource(finderBitmap.width, finderBitmap.height, sourceBuffer.array())
                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
                listeners.forEach { it(null, result.text, null) }
            } catch (e: Exception) {
            } finally {
                finderBitmap?.recycle()
                sourceBitmap?.recycle()
            }
        }
    }
}
