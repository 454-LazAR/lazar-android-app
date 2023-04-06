/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.lazar_android_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.get
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import org.tensorflow.lite.task.vision.segmenter.OutputType
import org.tensorflow.lite.task.vision.segmenter.Segmentation
import java.lang.Exception
import java.util.*
import kotlin.math.max

/**
 * Class responsible to run the Image Segmentation model. more information about the DeepLab model
 * being used can be found here:
 * https://ai.googleblog.com/2018/03/semantic-image-segmentation-with.html
 * https://github.com/tensorflow/models/tree/master/research/deeplab
 *
 * Label names: 'background', 'aeroplane', 'bicycle', 'bird', 'boat', 'bottle', 'bus', 'car', 'cat',
 * 'chair', 'cow', 'diningtable', 'dog', 'horse', 'motorbike', 'person', 'pottedplant', 'sheep',
 * 'sofa', 'train', 'tv'
 */
class ImageSegmentationHelper(
    var numThreads: Int = 2,
    var currentDelegate: Int = 0,
    val context: Context,
    val imageSegmentationListener: SegmentationListener?
) {
    private var imageSegmenter: ImageSegmenter? = null


    private var scaleBitmap: Bitmap? = null
    private var listener: OverlayViewListener? = null

    fun setOnOverlayViewListener(listener: OverlayViewListener) {
        this.listener = listener
    }


    init {
        setupImageSegmenter()
    }

    fun clearImageSegmenter() {
        imageSegmenter = null
    }

    private fun setupImageSegmenter() {
        // Create the base options for the segment
        val optionsBuilder =
            ImageSegmenter.ImageSegmenterOptions.builder()

        // Set general segmentation options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    baseOptionsBuilder.useGpu()
                } else {
                    imageSegmentationListener?.onError("GPU is not supported on this device")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        /*
        CATEGORY_MASK is being specifically used to predict the available objects
        based on individual pixels in this sample. The other option available for
        OutputType, CONFIDENCE_MAP, provides a gray scale mapping of the image
        where each pixel has a confidence score applied to it from 0.0f to 1.0f
         */
        optionsBuilder.setOutputType(OutputType.CATEGORY_MASK)
        try {
            imageSegmenter =
                ImageSegmenter.createFromFileAndOptions(
                    context,
                    MODEL_DEEPLABV3,
                    optionsBuilder.build()
                )
        } catch (e: IllegalStateException) {
            imageSegmentationListener?.onError(
                "Image segmentation failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun segment(image: Bitmap, imageRotation: Int) : Bitmap? {

        if (imageSegmenter == null) {
            setupImageSegmenter()
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        var inferenceTime = SystemClock.uptimeMillis()

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        val imageProcessor =
                ImageProcessor.Builder()
                        .add(Rot90Op(-imageRotation / 90))
                        .build()

        // Preprocess the image and convert it into a TensorImage for segmentation.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val segmentResult = imageSegmenter?.segment(tensorImage)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        imageSegmentationListener?.onResults(
                segmentResult,
                inferenceTime,
                tensorImage.height,
                tensorImage.width
        )

        if (segmentResult != null && segmentResult.isNotEmpty()) {
            val colorLabels = segmentResult[0].coloredLabels.mapIndexed { index, coloredLabel ->
                ColorLabel(
                        index,
                        coloredLabel.getlabel(),
                        coloredLabel.argb
                )
            }

            // Create the mask bitmap with colors and the set of detected labels.
            // We only need the first mask for this sample because we are using
            // the OutputType CATEGORY_MASK, which only provides a single mask.
            val maskTensor = segmentResult[0].masks[0]
            val test = segmentResult[0]

            val maskArray = maskTensor.buffer.array()
            val pixels = IntArray(maskArray.size)

            for (i in maskArray.indices) {
                // Set isExist flag to true if any pixel contains this color.
                val colorLabel = colorLabels[maskArray[i].toInt()].apply {
                    isExist = true
                }
                val color = colorLabel.getColor()
                if (colorLabel.label == "person") {
                    pixels[i] = color
                }
                else {
                    pixels[i] = 0
                }
            }

            val image = Bitmap.createBitmap(
                    pixels,
                    maskTensor.width,
                    maskTensor.height,
                    Bitmap.Config.ARGB_8888
            )

            // PreviewView is in FILL_START mode. So we need to scale up the bounding
            // box to match with the size that the captured images will be displayed.
//            val scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
//            val scaleWidth = (imageWidth * scaleFactor).toInt()
//            val scaleHeight = (imageHeight * scaleFactor).toInt()

            scaleBitmap = Bitmap.createScaledBitmap(image, tensorImage.width, tensorImage.height, false)
            listener?.onLabels(colorLabels.filter { it.isExist })
            return scaleBitmap
        }
        return null
    }


    interface SegmentationListener {
        fun onError(error: String)
        fun onResults(
            results: List<Segmentation>?,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    interface OverlayViewListener {
        fun onLabels(colorLabels: List<ColorLabel>)
    }

    data class ColorLabel(
            val id: Int,
            val label: String,
            val rgbColor: Int,
            var isExist: Boolean = false
    ) {

        private val ALPHA_COLOR = 128

        fun getColor(): Int {
            // Use completely transparent for the background color.
            return if (id == 0) Color.TRANSPARENT else Color.argb(
                    ALPHA_COLOR,
                    Color.red(rgbColor),
                    Color.green(rgbColor),
                    Color.blue(rgbColor)
            )
        }
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_DEEPLABV3 = "deeplabv3.tflite"

        private const val TAG = "Image Segmentation Helper"
    }
}
