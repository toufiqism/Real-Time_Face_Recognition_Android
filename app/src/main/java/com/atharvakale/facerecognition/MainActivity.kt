package com.atharvakale.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.Log
import android.util.Pair
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FaceRecognition"
        private const val SELECT_PICTURE = 1
        private const val MY_CAMERA_REQUEST_CODE = 100
        private const val MODEL_FILE = "mobile_face_net.tflite"
    }

    private lateinit var detector: FaceDetector
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private lateinit var previewView: PreviewView
    private lateinit var face_preview: ImageView
    private lateinit var tfLite: Interpreter
    private lateinit var reco_name: TextView
    private lateinit var preview_info: TextView
    private lateinit var textAbove_preview: TextView
    private lateinit var recognize: MaterialButton
    private lateinit var camera_switch: MaterialButton
    private lateinit var actions: MaterialButton
    private lateinit var add_face: ImageButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var instructionsFab: FloatingActionButton
    private lateinit var cameraSelector: CameraSelector
    private var developerMode = false
    private var distance = 1.0f
    private var start = true
    private var flipX = false
    private val context: Context = this
    private var cam_face = CameraSelector.LENS_FACING_BACK // Default Back Camera
    private var intValues: IntArray? = null
    private val inputSize = 112 // Input size for model
    private val isModelQuantized = false
    private var embeedings: Array<FloatArray>? = null
    private val IMAGE_MEAN = 128.0f
    private val IMAGE_STD = 128.0f
    private val OUTPUT_SIZE = 192 // Output size of model
    private var cameraProvider: ProcessCameraProvider? = null
    private val registered = HashMap<String, SimilarityClassifier.Recognition>() // saved Faces

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registered.putAll(readFromSP()) // Load saved faces from memory when app starts
        Log.d(TAG, "onCreate: loaded recognitions count=${registered.size}")
        setContentView(R.layout.activity_main)

        // Setup Toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        face_preview = findViewById(R.id.imageView)
        reco_name = findViewById(R.id.textView)
        preview_info = findViewById(R.id.textView2)
        textAbove_preview = findViewById(R.id.textAbovePreview)
        add_face = findViewById(R.id.imageButton)
        instructionsFab = findViewById(R.id.fabInstructions)
        add_face.visibility = View.INVISIBLE

        val sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
        Log.d(TAG, "onCreate: loaded distance threshold=$distance")

        face_preview.visibility = View.INVISIBLE
        recognize = findViewById(R.id.button3)
        camera_switch = findViewById(R.id.button5)
        actions = findViewById(R.id.button2)
        textAbove_preview.text = getString(R.string.label_recognized_face)

        // Camera Permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), MY_CAMERA_REQUEST_CODE)
            Log.d(TAG, "onCreate: requested camera permission")
        } else {
            Log.d(TAG, "onCreate: camera permission already granted")
        }

        // Instructions FAB
        instructionsFab.setOnClickListener {
            val message = preview_info.text?.toString()?.takeIf { it.isNotBlank() }
                ?: "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."

            AlertDialog.Builder(context)
                .setTitle(getString(R.string.label_recognized_face))
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        // On-screen Action Button
        actions.setOnClickListener {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Select Action:")

            // add a checkbox list
            val names = arrayOf(
                "View Recognition List",
                "Update Recognition List",
                "Save Recognitions",
                "Load Recognitions",
                "Clear All Recognitions",
                "Import Photo (Beta)",
                "Hyperparameters",
                "Developer Mode"
            )

            builder.setItems(names) { dialog, which ->
                when (which) {
                    0 -> displaynameListview()
                    1 -> updatenameListview()
                    2 -> insertToSP(registered, 0) // mode: 0:save all, 1:clear all, 2:update all
                    3 -> registered.putAll(readFromSP())
                    4 -> clearnameList()
                    5 -> loadphoto()
                    6 -> testHyperparameter()
                    7 -> developerMode()
                }
            }

            builder.setPositiveButton("OK") { dialog, which -> }
            builder.setNegativeButton("Cancel", null)

            // create and show the alert dialog
            val dialog = builder.create()
            dialog.show()
            Log.d(TAG, "Actions dialog opened with options count=${names.size}")
        }

        // On-screen switch to toggle between Cameras.
        camera_switch.setOnClickListener {
            if (cam_face == CameraSelector.LENS_FACING_BACK) {
                cam_face = CameraSelector.LENS_FACING_FRONT
                flipX = true
            } else {
                cam_face = CameraSelector.LENS_FACING_BACK
                flipX = false
            }
            cameraProvider?.unbindAll()
            Log.d(TAG, "Camera switched. Using lens facing=$cam_face flipX=$flipX")
            cameraBind()
        }

        add_face.setOnClickListener {
            addFace()
        }

        recognize.setOnClickListener {
            if (recognize.text.toString() == "Recognize") {
                start = true
                textAbove_preview.text = getString(R.string.label_recognized_face)
                recognize.text = "Add Face"
                add_face.visibility = View.INVISIBLE
                reco_name.visibility = View.VISIBLE
                face_preview.visibility = View.INVISIBLE
                preview_info.text = ""
                Log.d(TAG, "recognize button: switched to recognize mode")
            } else {
                textAbove_preview.text = "Face Preview: "
                recognize.text = "Recognize"
                add_face.visibility = View.VISIBLE
                reco_name.visibility = View.INVISIBLE
                face_preview.visibility = View.VISIBLE
//                preview_info.text =
//                    "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                Log.d(TAG, "recognize button: switched to add-face mode")
            }
        }

        // Load model
        try {
            tfLite = Interpreter(loadModelFile(this@MainActivity, MODEL_FILE))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Initialize Face Detector
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(highAccuracyOpts)

        cameraBind()
    }

    private fun testHyperparameter() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select Hyperparameter:")

        // add a checkbox list
        val names = arrayOf("Maximum Nearest Neighbour Distance")

        builder.setItems(names) { dialog, which ->
            when (which) {
                0 -> hyperparameters()
            }
        }

        builder.setPositiveButton("OK") { dialog, which -> }
        builder.setNegativeButton("Cancel", null)

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun developerMode() {
        developerMode = !developerMode
        val message = if (developerMode) "Developer Mode ON" else "Developer Mode OFF"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "developerMode: ${if (developerMode) "enabled" else "disabled"}")
    }

    private fun addFace() {
        start = false
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Enter Name")

        // Set up the input
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("ADD") { dialog, which ->
            // Create and Initialize new object with Face embeddings and Name.
            val result = SimilarityClassifier.Recognition("0", "", -1f)
            result.extra = embeedings

            registered[input.text.toString()] = result
            Log.d(
                TAG,
                "addFace: added name=${input.text} embeddingLength=${embeedings?.get(0)?.size ?: 0}"
            )
            start = true
        }

        builder.setNegativeButton("Cancel") { dialog, which ->
            start = true
            dialog.cancel()
        }

        builder.show()
    }

    private fun clearnameList() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Do you want to delete all Recognitions?")
        builder.setPositiveButton("Delete All") { dialog, which ->
            registered.clear()
            Toast.makeText(context, "Recognitions Cleared", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "clearnameList: all recognitions cleared")
        }
        insertToSP(registered, 1)
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun updatenameListview() {
        val builder = AlertDialog.Builder(context)
        if (registered.isEmpty()) {
            builder.setTitle("No Faces Added!!")
            builder.setPositiveButton("OK", null)
        } else {
            builder.setTitle("Select Recognition to delete:")

            // add a checkbox list
            val names = arrayOfNulls<String>(registered.size)
            val checkedItems = BooleanArray(registered.size)
            var i = 0
            for ((key, _) in registered) {
                names[i] = key
                checkedItems[i] = false
                i++
            }

            builder.setMultiChoiceItems(names, checkedItems) { dialog, which, isChecked ->
                // user checked or unchecked a box
                checkedItems[which] = isChecked
                Log.v(TAG, "updatenameListview: name=${names[which]} checked=$isChecked")
            }

            builder.setPositiveButton("OK") { dialog, which ->
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        registered.remove(names[i])
                        Log.d(TAG, "updatenameListview: removed recognition name=${names[i]}")
                    }
                }
                insertToSP(registered, 2) // mode: 0:save all, 1:clear all, 2:update all
                Toast.makeText(context, "Recognitions Updated", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Cancel", null)

            // create and show the alert dialog
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun hyperparameters() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Euclidean Distance")
        builder.setMessage(
            "0.00 -> Perfect Match\n1.00 -> Default\nTurn On Developer Mode to find optimum value\n\nCurrent Value:"
        )
        // Set up the input
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        builder.setView(input)
        val sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
        input.setText(distance.toString())
        // Set up the buttons
        builder.setPositiveButton("Update") { dialog, which ->
            distance = input.text.toString().toFloat()
            Log.d(TAG, "hyperparameters: updated distance threshold=$distance")

            val sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.putFloat("distance", distance)
            editor.apply()
        }
        builder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun displaynameListview() {
        val builder = AlertDialog.Builder(context)
        if (registered.isEmpty())
            builder.setTitle("No Faces Added!!")
        else
            builder.setTitle("Recognitions:")

        // add a checkbox list
        val names = arrayOfNulls<String>(registered.size)
        val checkedItems = BooleanArray(registered.size)
        var i = 0
        for ((key, _) in registered) {
            names[i] = key
            checkedItems[i] = false
            i++
        }
        builder.setItems(names, null)
        Log.d(TAG, "displaynameListview: showing recognitions count=${names.size}")

        builder.setPositiveButton("OK") { dialog, which -> }

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
        Log.d(TAG, "displaynameListview: dialog displayed")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show()
                Log.d(TAG, "onRequestPermissionsResult: camera permission granted")
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
                Log.w(TAG, "onRequestPermissionsResult: camera permission denied")
            }
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity, MODEL_FILE: String): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        Log.d(TAG, "loadModelFile: model=$MODEL_FILE declaredLength=$declaredLength")
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Bind camera and preview view
    private fun cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        previewView = findViewById(R.id.previewView)
        Log.d(TAG, "cameraBind: awaiting camera provider")
        cameraProviderFuture?.addListener({
            try {
                cameraProvider = cameraProviderFuture?.get()

                cameraProvider?.let { bindPreview(it) }
                Log.d(TAG, "cameraBind: camera provider ready")
            } catch (e: ExecutionException) {
                Log.e(TAG, "cameraBind: failed to get camera provider", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "cameraBind: failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(@NonNull cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cam_face)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Latest frame is shown
            .build()

        Log.d(TAG, "bindPreview: configured cameraSelector lensFacing=$cam_face")
        Log.d(TAG, "bindPreview: ImageAnalysis targetResolution=640x480 strategy=KEEP_ONLY_LATEST")
        val executor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            try {
                Thread.sleep(0) // Camera preview refreshed every 10 millisec(adjust as required)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            @SuppressLint("UnsafeExperimentalUsageError")
            // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)
            val mediaImage = imageProxy.image

            val image = if (mediaImage != null) {
                InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
            } else {
                null
            }

            if (image != null) {
                Log.v(TAG, "analyze: frame rotationDegrees=${imageProxy.imageInfo.rotationDegrees}")
            }

            // Process acquired image to detect faces
            if (image != null && mediaImage != null) {
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0] // Get first face from detected faces

                            // mediaImage to Bitmap
                            val frame_bmp = toBitmap(mediaImage)

                            val rot = imageProxy.imageInfo.rotationDegrees

                            // Adjust orientation of Face
                            var frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false)

                            // Get bounding box of face
                            val boundingBox = RectF(face.boundingBox)

                            // Crop out bounding box from whole Bitmap(image)
                            var cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox)
                            Log.v(
                                TAG,
                                "analyze: cropped face size=${cropped_face.width}x${cropped_face.height}"
                            )

                            if (flipX)
                                cropped_face = rotateBitmap(cropped_face, 0, flipX, false)
                            // Scale the acquired Face to 112*112 which is required input for model
                            val scaled = getResizedBitmap(cropped_face, 112, 112)
                            Log.v(
                                TAG,
                                "analyze: scaled face size=${scaled.width}x${scaled.height}"
                            )

                            if (start)
                                recognizeImage(scaled) // Send scaled bitmap to create face embeddings.
                        } else {
                            if (registered.isEmpty())
                                reco_name.text = "Add Face"
                            else
                                reco_name.text = "No Face Detected!"
                            Log.v(
                                TAG,
                                "analyze: no faces detected registeredCount=${registered.size}"
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        Log.e(TAG, "analyze: face detection failure", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close() // v.important to acquire next frame for analysis
                        Log.v(TAG, "analyze: imageProxy closed")
                    }
            } else {
                imageProxy.close()
            }
        }

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
        Log.d(TAG, "bindPreview: camera bound to lifecycle")
    }

    fun recognizeImage(bitmap: Bitmap) {
        // set Face to Preview
        face_preview.setImageBitmap(bitmap)
        Log.v(TAG, "recognizeImage: processing bitmap size=${bitmap.width}x${bitmap.height}")

        // Create ByteBuffer to store normalized image
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())

        intValues = IntArray(inputSize * inputSize)

        // get pixel values from Bitmap to normalize
        bitmap.getPixels(
            intValues!!,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        imgData.rewind()

        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues!![i * inputSize + j]
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put(((pixelValue shr 16) and 0xFF).toByte())
                    imgData.put(((pixelValue shr 8) and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData.putFloat((((pixelValue shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat((((pixelValue shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        // imgData is input to our model
        val inputArray = arrayOf<Any>(imgData)

        val outputMap = HashMap<Int, Any>()

        embeedings = Array(1) { FloatArray(OUTPUT_SIZE) } // output of model will be stored in this variable

        outputMap[0] = embeedings!!

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap) // Run model

        val embeddingLength = if (embeedings != null && embeedings!!.isNotEmpty()) {
            embeedings!![0].size
        } else {
            0
        }
        if (embeddingLength > 0) {
            val sample = Arrays.copyOfRange(
                embeedings!![0],
                0,
                minOf(5, embeddingLength)
            )
            Log.d(
                TAG,
                "recognizeImage: embedding generated length=$embeddingLength sample=${Arrays.toString(sample)}"
            )
            logEmbeddingArray("Current embedding", embeedings!![0])
        } else {
            Log.w(TAG, "recognizeImage: embedding array empty")
        }

        var distance_local = Float.MAX_VALUE
        val id = "0"
        var label = "?"

        // Compare new face with saved Faces.
        if (registered.isNotEmpty()) {
            val nearest = findNearest(embeedings!![0]) // Find 2 closest matching face

            if (nearest.isNotEmpty() && nearest[0] != null) {
                val name = nearest[0].first // get name and distance of closest matching face
                distance_local = nearest[0].second
                Log.d(
                    TAG,
                    "recognizeImage: nearest name=$name distance=$distance_local threshold=$distance"
                )
                if (developerMode) {
                    val secondNearest = if (nearest.size > 1) nearest[1] else Pair("N/A", Float.MAX_VALUE)
                    if (distance_local < distance) // If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        reco_name.text =
                            "Nearest: $name\nDist: ${String.format("%.3f", distance_local)}\n2nd Nearest: ${secondNearest.first}\nDist: ${String.format("%.3f", secondNearest.second)}"
                    else
                        reco_name.text =
                            "Unknown\nDist: ${String.format("%.3f", distance_local)}\nNearest: $name\nDist: ${String.format("%.3f", distance_local)}\n2nd Nearest: ${secondNearest.first}\nDist: ${String.format("%.3f", secondNearest.second)}"
                } else {
                    if (distance_local < distance) // If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        reco_name.text = name
                    else
                        reco_name.text = "Unknown"
                }
            }
        }
    }

    // Compare Faces by distance between face embeddings
    private fun findNearest(emb: FloatArray): List<Pair<String, Float>> {
        val neighbour_list = ArrayList<Pair<String, Float>>()
        var ret: Pair<String, Float>? = null // to get closest match
        var prev_ret: Pair<String, Float>? = null // to get second closest match
        for ((name, recognition) in registered) {
            val extra = recognition.extra
            if (extra !is Array<*>) {
                Log.w(
                    TAG,
                    "findNearest: skipping candidate=$name because embedding type is ${extra?.javaClass?.name ?: "null"}"
                )
                continue
            }
            val storedEmbeddings = extra as? Array<FloatArray>
            if (storedEmbeddings == null || storedEmbeddings.isEmpty() || storedEmbeddings[0] == null) {
                Log.w(TAG, "findNearest: skipping candidate=$name due to empty embedding array")
                continue
            }
            val knownEmb = storedEmbeddings[0]
            logEmbeddingArray("Stored embedding for $name", knownEmb)

            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = kotlin.math.sqrt(distance)
            Log.v(TAG, "findNearest: candidate=$name distance=$distance")
            if (ret == null || distance < ret.second) {
                prev_ret = ret
                ret = Pair(name, distance)
            }
        }
        if (prev_ret == null) prev_ret = ret
        neighbour_list.add(ret ?: Pair("", Float.MAX_VALUE))
        neighbour_list.add(prev_ret ?: Pair("", Float.MAX_VALUE))

        return neighbour_list
    }

    private fun logEmbeddingArray(label: String, embedding: FloatArray?) {
        if (!developerMode) {
            return
        }
        if (embedding == null) {
            Log.w(TAG, "$label: embedding array is null")
            return
        }
        val full = Arrays.toString(embedding)
        val maxLogLength = 3000
        var chunkIndex = 0
        var start = 0
        while (start < full.length) {
            val end = minOf(full.length, start + maxLogLength)
            val chunk = full.substring(start, end)
            Log.d(TAG, "$label length=${embedding.size} chunk=$chunkIndex: $chunk")
            chunkIndex++
            start += maxLogLength
        }
    }

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    private fun getCropBitmapByCPU(source: Bitmap, cropRectF: RectF): Bitmap {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val cavas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        cavas.drawRect(
            RectF(0f, 0f, cropRectF.width(), cropRectF.height()),
            paint
        )

        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)

        cavas.drawBitmap(source, matrix, paint)

        if (source != null && !source.isRecycled) {
            source.recycle()
        }

        return resultBitmap
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int,
        flipX: Boolean,
        flipY: Boolean
    ): Bitmap {
        val matrix = Matrix()

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror the image along the X or Y axis.
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    // IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)

        var pos = 0

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride
                yBuffer.position(yBufferPos.toInt())
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride

        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)

        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel = vBuffer[1]
            try {
                val invertedPixel = (savePixel.toInt().inv() and 0xFF).toByte()
                vBuffer.put(1, invertedPixel)
                if (uBuffer[0] == invertedPixel) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer.get(nv21, ySize, 1)
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining())

                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }

        return nv21
    }

    private fun toBitmap(image: Image): Bitmap {
        val nv21 = YUV_420_888toNV21(image)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, yuvImage.width, yuvImage.height),
            75,
            out
        )

        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // Save Faces to Shared Preferences.Conversion of Recognition objects to json string
    private fun insertToSP(jsonMap: HashMap<String, SimilarityClassifier.Recognition>, mode: Int) {
        val mapToSave = when (mode) {
            1 -> { // mode: 0:save all, 1:clear all, 2:update all
                jsonMap.clear()
                jsonMap
            }
            0 -> {
                val existing = readFromSP()
                existing.putAll(jsonMap)
                existing
            }
            else -> jsonMap
        }
        val jsonString = Gson().toJson(mapToSave)
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("map", jsonString)
        editor.apply()
        Log.d(
            TAG,
            "insertToSP: mode=$mode savedCount=${mapToSave.size} jsonLength=${jsonString.length}"
        )
        Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show()
    }

    // Load Faces from Shared Preferences.Json String to Recognition object
    private fun readFromSP(): HashMap<String, SimilarityClassifier.Recognition> {
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val defValue = Gson().toJson(HashMap<String, SimilarityClassifier.Recognition>())
        val json = sharedPreferences.getString("map", defValue) ?: defValue
        val token = object : TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {}
        val retrievedMap = Gson().fromJson<HashMap<String, SimilarityClassifier.Recognition>>(
            json,
            token.type
        ) ?: HashMap()

        // During type conversion and save/load procedure,format changes(eg float converted to double).
        // So embeddings need to be extracted from it in required format(eg.double to float).
        for ((_, recognition) in retrievedMap) {
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            var arrayList = recognition.extra as? ArrayList<*>
            arrayList = arrayList?.get(0) as? ArrayList<*>
            if (arrayList != null) {
                for (counter in arrayList.indices) {
                    output[0][counter] = (arrayList[counter] as? Double)?.toFloat() ?: 0f
                }
            }
            recognition.extra = output
        }
        Log.d(
            TAG,
            "readFromSP: retrievedCount=${retrievedMap.size} rawJsonLength=${json.length} rawjson: $json"
        )
        Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }

    // Load Photo from phone storage
    private fun loadphoto() {
        start = false
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE)
    }

    // Similar Analyzing Procedure
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode resultCode=$resultCode")
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == SELECT_PICTURE) {
                val selectedImageUri = data.data
                Log.d(TAG, "loadphoto: selected uri=$selectedImageUri")
                if (selectedImageUri != null) {
                    try {
                        val impphoto = InputImage.fromBitmap(
                            getBitmapFromUri(selectedImageUri),
                            0
                        )
                        detector.process(impphoto)
                            .addOnSuccessListener { faces ->
                                Log.d(TAG, "loadphoto: detected faces count=${faces.size}")

                                if (faces.isNotEmpty()) {
                                    recognize.text = "Recognize"
                                    add_face.visibility = View.VISIBLE
                                    reco_name.visibility = View.INVISIBLE
                                    face_preview.visibility = View.VISIBLE
//                                    preview_info.text =
//                                        "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                                    val face = faces[0]

                                    // write code to recreate bitmap from source
                                    // Write code to show bitmap to canvas

                                    var frame_bmp: Bitmap? = null
                                    try {
                                        frame_bmp = getBitmapFromUri(selectedImageUri)
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }

                                    if (frame_bmp != null) {
                                        var frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false)

                                        val boundingBox = RectF(face.boundingBox)

                                        val cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox)

                                        val scaled = getResizedBitmap(cropped_face, 112, 112)

                                        recognizeImage(scaled)
                                        addFace()
                                        try {
                                            Thread.sleep(100)
                                        } catch (e: InterruptedException) {
                                            e.printStackTrace()
                                        }
                                    }
                                } else {
                                    Log.w(TAG, "loadphoto: no face detected in selected image")
                                }
                            }
                            .addOnFailureListener { e ->
                                start = true
                                Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "loadphoto: face detection failed", e)
                            }
                        face_preview.setImageBitmap(getBitmapFromUri(selectedImageUri))
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor = parcelFileDescriptor?.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor?.close()
        return image ?: throw IOException("Failed to decode bitmap from URI")
    }
}

