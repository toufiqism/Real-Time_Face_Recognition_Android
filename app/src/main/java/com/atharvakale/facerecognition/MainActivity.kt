package com.atharvakale.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import android.util.Log
import android.util.Pair
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import androidx.camera.core.Preview as CameraPreview

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "FaceRecognition"
        private const val MODEL_FILE = "mobile_face_net.tflite"
    }

    private lateinit var detector: FaceDetector
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private lateinit var tfLite: Interpreter
    private var cameraProvider: ProcessCameraProvider? = null
    private val registered = HashMap<String, SimilarityClassifier.Recognition>() // saved Faces

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registered.putAll(readFromSP()) // Load saved faces from memory when app starts
        Log.d(TAG, "onCreate: loaded recognitions count=${registered.size}")

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

        setContent {
            FaceRecognitionTheme {
                MainScreen(
                    activity = this@MainActivity,
                    detector = detector,
                    tfLite = tfLite,
                    registered = registered,
                    onRegisteredChanged = { registered.putAll(it) },
                    onCameraProviderReady = { cameraProvider = it },
                    cameraProvider = cameraProvider
                )
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
        Toast.makeText(this, "Recognitions Saved", Toast.LENGTH_SHORT).show()
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
        val OUTPUT_SIZE = 192
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
        Toast.makeText(this, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }
}

@Composable
fun MainScreen(
    activity: MainActivity,
    detector: FaceDetector,
    tfLite: Interpreter,
    registered: HashMap<String, SimilarityClassifier.Recognition>,
    onRegisteredChanged: (HashMap<String, SimilarityClassifier.Recognition>) -> Unit,
    onCameraProviderReady: (ProcessCameraProvider) -> Unit,
    cameraProvider: ProcessCameraProvider?
) {
    Log.d("FaceRecognition", "=== MainScreen composable called - NEW CODE VERSION ===")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Local camera provider state
    var localCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(cameraProvider) }

    // State management
    var start by remember { mutableStateOf(true) }
    var developerMode by remember { mutableStateOf(false) }
    var distance by remember {
        val sharedPref = context.getSharedPreferences("Distance", Context.MODE_PRIVATE)
        mutableFloatStateOf(sharedPref.getFloat("distance", 1.00f))
    }
    var flipX by remember { mutableStateOf(false) }
    var camFace by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    // Camera switch trigger - increments to force LaunchedEffect to run
    var cameraSwitchTrigger by remember { mutableStateOf(0) }
    var faceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recoName by remember { mutableStateOf("Add Face") }
    var previewInfo by remember { mutableStateOf("") }
    var textAbovePreview by remember { mutableStateOf("Recognized Face:") }
    var isAddFaceVisible by remember { mutableStateOf(false) }
    var isFacePreviewVisible by remember { mutableStateOf(false) }
    var isRecoNameVisible by remember { mutableStateOf(true) }
    var recognizeButtonText by remember { mutableStateOf("Add Face") }

    // Dialog states
    var showActionsDialog by remember { mutableStateOf(false) }
    var showAddFaceDialog by remember { mutableStateOf(false) }
    var showViewRecognitionDialog by remember { mutableStateOf(false) }
    var showUpdateRecognitionDialog by remember { mutableStateOf(false) }
    var showClearRecognitionDialog by remember { mutableStateOf(false) }
    var showHyperparameterDialog by remember { mutableStateOf(false) }
    var showHyperparameterSelectDialog by remember { mutableStateOf(false) }
    var selectedNamesForUpdate by remember { mutableStateOf(setOf<String>()) }

    // Camera permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("FaceRecognition", "Camera permission granted")
            } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_LONG).show()
            Log.w("FaceRecognition", "Camera permission denied")
        }
    }

    // Image selection launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleImageSelection(
                uri = uri,
                context = context,
                detector = detector,
                tfLite = tfLite,
                registered = registered,
                flipX = flipX,
                onFaceBitmapChanged = { faceBitmap = it },
                onRecognizeButtonTextChanged = { recognizeButtonText = it },
                onAddFaceVisibleChanged = { isAddFaceVisible = it },
                onRecoNameVisibleChanged = { isRecoNameVisible = it },
                onFacePreviewVisibleChanged = { isFacePreviewVisible = it },
                onPreviewInfoChanged = { previewInfo = it },
                onTextAbovePreviewChanged = { textAbovePreview = it },
                onStartChanged = { start = it },
                onShowAddFaceDialog = { showAddFaceDialog = true }
            )
        }
    }

    // Check camera permission on launch
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Camera setup - Create PreviewView once and reuse it (don't recreate on camera switch)
    // Recreating causes timeout issues because the new view isn't attached when we try to bind
    val previewView = remember { 
        Log.d("FaceRecognition", "PreviewView created. Will be reused for all camera switches.")
        PreviewView(context) 
    }
    var cameraSelector: CameraSelector? by remember { mutableStateOf(null) }
    var imageAnalysis: ImageAnalysis? by remember { mutableStateOf(null) }
    var embeedings: Array<FloatArray>? by remember { mutableStateOf(null) }
    val intValues = remember { IntArray(112 * 112) }
    val inputSize = 112
    val isModelQuantized = false
    val IMAGE_MEAN = 128.0f
    val IMAGE_STD = 128.0f
    val OUTPUT_SIZE = 192
    val imageAnalysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Initialize camera provider
    LaunchedEffect(Unit) {
        if (localCameraProvider == null) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    localCameraProvider = provider
                    onCameraProviderReady(provider)
                } catch (e: ExecutionException) {
                    Log.e("FaceRecognition", "Failed to get camera provider", e)
                } catch (e: InterruptedException) {
                    Log.e("FaceRecognition", "Failed to get camera provider", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
    
    // Update local camera provider when external one changes
    LaunchedEffect(cameraProvider) {
        Log.d("FaceRecognition", "Camera provider changed. cameraProvider=${cameraProvider != null}")
        localCameraProvider = cameraProvider
    }
    
    // Log state changes
    SideEffect {
        Log.d("FaceRecognition", "[SideEffect] State changed. camFace=$camFace flipX=$flipX trigger=$cameraSwitchTrigger")
    }
    
    // Bind camera when provider is ready or camera face changes
    // Use snapshotFlow to reliably detect state changes
    LaunchedEffect(localCameraProvider) {
        if (localCameraProvider == null) {
            Log.d("FaceRecognition", "LaunchedEffect: Waiting for camera provider...")
            return@LaunchedEffect
        }
        
        Log.d("FaceRecognition", "LaunchedEffect: Starting snapshotFlow collection")
        snapshotFlow { 
            Triple(camFace, flipX, cameraSwitchTrigger) 
        }.collect { (currentCamFace, currentFlipX, currentTrigger) ->
            Log.d("FaceRecognition", "LaunchedEffect triggered via snapshotFlow. camFace=$currentCamFace flipX=$currentFlipX trigger=$currentTrigger localCameraProvider=${localCameraProvider != null}")
            if (localCameraProvider != null) {
                try {
                    Log.d("FaceRecognition", "[UNBIND] Starting unbind process. camFace=$currentCamFace flipX=$currentFlipX")
                    Log.d("FaceRecognition", "[UNBIND] imageAnalysis state: ${imageAnalysis != null}")
                    // Clear existing analyzer before unbinding
                    if (imageAnalysis != null) {
                        Log.d("FaceRecognition", "[UNBIND] Clearing analyzer")
                        imageAnalysis?.clearAnalyzer()
                    }
                    Log.d("FaceRecognition", "[UNBIND] Calling unbindAll()")
                    localCameraProvider?.unbindAll()
                    Log.d("FaceRecognition", "[UNBIND] unbindAll() called, waiting 300ms...")
                    // Longer delay to ensure unbind completes, resources are released, and view is ready
                    kotlinx.coroutines.delay(300)
                    Log.d("FaceRecognition", "[UNBIND] Unbind complete, proceeding to rebind")
                } catch (e: Exception) {
                    Log.e("FaceRecognition", "[UNBIND] Error unbinding camera", e)
                }
                try {
                    Log.d("FaceRecognition", "[BIND] Starting bind process. camFace=$currentCamFace flipX=$currentFlipX")
                    Log.d("FaceRecognition", "[BIND] PreviewView instance: ${previewView.hashCode()}")
                    Log.d("FaceRecognition", "[BIND] PreviewView parent: ${previewView.parent}")
                    Log.d("FaceRecognition", "[BIND] PreviewView isAttachedToWindow: ${previewView.isAttachedToWindow}")
                    Log.d("FaceRecognition", "[BIND] PreviewView width: ${previewView.width}, height: ${previewView.height}")
                    
                    // Ensure PreviewView is ready before binding
                    if (!previewView.isAttachedToWindow) {
                        Log.w("FaceRecognition", "[BIND] PreviewView not attached yet, waiting 100ms...")
                        kotlinx.coroutines.delay(100)
                    }
                    
                    val result = bindCamera(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        cameraProvider = localCameraProvider!!,
                        previewView = previewView,
                        camFace = currentCamFace,
                        detector = detector,
                        tfLite = tfLite,
                        registered = registered,
                        start = start,
                        flipX = currentFlipX,
                        distance = distance,
                        developerMode = developerMode,
                        intValues = intValues,
                        inputSize = inputSize,
                        isModelQuantized = isModelQuantized,
                        IMAGE_MEAN = IMAGE_MEAN,
                        IMAGE_STD = IMAGE_STD,
                        OUTPUT_SIZE = OUTPUT_SIZE,
                        executor = imageAnalysisExecutor,
                        onCameraSelectorReady = { cameraSelector = it },
                        onEmbeedingsChanged = { embeedings = it },
                        onFaceBitmapChanged = { faceBitmap = it },
                        onRecoNameChanged = { recoName = it }
                    )
                    imageAnalysis = result
                    Log.d("FaceRecognition", "[BIND] Camera rebound successfully. camFace=$currentCamFace flipX=$currentFlipX imageAnalysis=${result.hashCode()}")
                } catch (e: Exception) {
                    Log.e("FaceRecognition", "[BIND] Error binding camera", e)
                    e.printStackTrace()
                }
            } else {
                Log.w("FaceRecognition", "LaunchedEffect triggered but localCameraProvider is null")
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Camera Preview
            Box(
                modifier = Modifier
                    .width(297.dp)
                    .height(279.dp)
            ) {
                if (localCameraProvider != null) {
                    AndroidView(
                        factory = { 
                            Log.d("FaceRecognition", "[AndroidView] Factory called. previewView=${previewView.hashCode()}")
                            previewView 
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            Log.d("FaceRecognition", "[AndroidView] Update called. view=${view.hashCode()} camFace=$camFace")
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Initializing camera...")
                    }
                }

                // Camera switch button
                FloatingActionButton(
                    onClick = {
                        Log.e("FaceRecognition", "!!!!!!!!! BUTTON CLICKED - NEW CODE !!!!!!!!!")
                        val oldCamFace = camFace
                        val oldFlipX = flipX
                        Log.d("FaceRecognition", "[BUTTON] Camera switch button clicked!")
                        Log.d("FaceRecognition", "[BUTTON] Current state: camFace=$oldCamFace flipX=$oldFlipX")
                        
                        // Update camera face - LaunchedEffect will handle unbinding and rebinding
                        val newCamFace = if (camFace == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                        val newFlipX = newCamFace == CameraSelector.LENS_FACING_FRONT
                        
                        Log.d("FaceRecognition", "[BUTTON] Setting new state: camFace=$newCamFace flipX=$newFlipX")
                        camFace = newCamFace
                        flipX = newFlipX
                        cameraSwitchTrigger++ // Force LaunchedEffect to trigger
                        Log.d("FaceRecognition", "[BUTTON] State updated. camFace=$camFace flipX=$flipX trigger=$cameraSwitchTrigger")
                        Log.d("FaceRecognition", "[BUTTON] LaunchedEffect should trigger now")
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Switch Camera"
                    )
                }
            }

            // Text above preview
            Text(
                text = textAbovePreview,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // Face preview or Recognition name
            Box(
                modifier = Modifier
                    .width(203.dp)
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isFacePreviewVisible && faceBitmap != null) {
                    Image(
                        bitmap = faceBitmap!!.asImageBitmap(),
                        contentDescription = "Face Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (isAddFaceVisible) {
                        FloatingActionButton(
                            onClick = { showAddFaceDialog = true },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Face"
                            )
                        }
                    }
                } else if (isRecoNameVisible) {
                    Text(
                        text = recoName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Preview info text
                if (previewInfo.isNotEmpty()) {
                    Text(
                        text = previewInfo,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (recognizeButtonText == "Recognize") {
            start = true
                            textAbovePreview = "Recognized Face:"
                            recognizeButtonText = "Add Face"
                            isAddFaceVisible = false
                            isRecoNameVisible = true
                            isFacePreviewVisible = false
                            previewInfo = ""
                            Log.d("FaceRecognition", "recognize button: switched to recognize mode")
                        } else {
                            textAbovePreview = "Face Preview: "
                            recognizeButtonText = "Recognize"
                            isAddFaceVisible = true
                            isRecoNameVisible = false
                            isFacePreviewVisible = true
                            previewInfo =
                                "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                            Log.d("FaceRecognition", "recognize button: switched to add-face mode")
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(recognizeButtonText)
                }

                Button(
                    onClick = { showActionsDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ACTIONS")
                }
            }
        }
    }

    // Dialogs
    if (showActionsDialog) {
        ActionsDialog(
            onDismiss = { showActionsDialog = false },
            onViewRecognition = { showViewRecognitionDialog = true },
            onUpdateRecognition = { showUpdateRecognitionDialog = true },
            onSaveRecognitions = {
                val mapToSave = HashMap(registered)
                val jsonString = Gson().toJson(mapToSave)
                val sharedPreferences = context.getSharedPreferences("HashMap", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("map", jsonString)
                editor.apply()
                Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show()
                showActionsDialog = false
            },
            onLoadRecognitions = {
                val loaded = readFromSP(context)
                onRegisteredChanged(loaded)
                showActionsDialog = false
            },
            onClearRecognitions = { showClearRecognitionDialog = true },
            onImportPhoto = {
                imagePickerLauncher.launch("image/*")
                showActionsDialog = false
            },
            onHyperparameters = { showHyperparameterSelectDialog = true },
            onDeveloperMode = {
                developerMode = !developerMode
                Toast.makeText(
                    context,
                    if (developerMode) "Developer Mode ON" else "Developer Mode OFF",
                    Toast.LENGTH_SHORT
                ).show()
                showActionsDialog = false
            }
        )
    }

    if (showAddFaceDialog) {
        AddFaceDialog(
            onDismiss = { showAddFaceDialog = false },
            onAdd = { name ->
                val result = SimilarityClassifier.Recognition("0", "", -1f)
                result.extra = embeedings
                registered[name] = result
                onRegisteredChanged(registered)
                start = true
                showAddFaceDialog = false
                Log.d("FaceRecognition", "addFace: added name=$name")
            }
        )
    }

    if (showViewRecognitionDialog) {
        ViewRecognitionDialog(
            registered = registered,
            onDismiss = { showViewRecognitionDialog = false }
        )
    }

    if (showUpdateRecognitionDialog) {
        UpdateRecognitionDialog(
            registered = registered,
            selectedNames = selectedNamesForUpdate,
            onSelectedNamesChanged = { selectedNamesForUpdate = it },
            onDismiss = { showUpdateRecognitionDialog = false },
            onUpdate = {
                selectedNamesForUpdate.forEach { name ->
                    registered.remove(name)
                }
                onRegisteredChanged(registered)
                val mapToSave = HashMap(registered)
                val jsonString = Gson().toJson(mapToSave)
                val sharedPreferences = context.getSharedPreferences("HashMap", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("map", jsonString)
                editor.apply()
                Toast.makeText(context, "Recognitions Updated", Toast.LENGTH_SHORT).show()
                selectedNamesForUpdate = setOf()
                showUpdateRecognitionDialog = false
            }
        )
    }

    if (showClearRecognitionDialog) {
        ClearRecognitionDialog(
            onDismiss = { showClearRecognitionDialog = false },
            onConfirm = {
                registered.clear()
                val sharedPreferences = context.getSharedPreferences("HashMap", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("map", Gson().toJson(HashMap<String, SimilarityClassifier.Recognition>()))
                editor.apply()
                onRegisteredChanged(registered)
                Toast.makeText(context, "Recognitions Cleared", Toast.LENGTH_SHORT).show()
                showClearRecognitionDialog = false
            }
        )
    }

    if (showHyperparameterSelectDialog) {
        HyperparameterSelectDialog(
            onDismiss = { showHyperparameterSelectDialog = false },
            onSelect = { showHyperparameterDialog = true }
        )
    }

    if (showHyperparameterDialog) {
        HyperparameterDialog(
            currentDistance = distance,
            onDismiss = { showHyperparameterDialog = false },
            onUpdate = { newDistance ->
                distance = newDistance
                val sharedPref = context.getSharedPreferences("Distance", Context.MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.putFloat("distance", distance)
            editor.apply()
                Log.d("FaceRecognition", "hyperparameters: updated distance threshold=$distance")
                showHyperparameterDialog = false
            }
        )
    }
}

@Composable
fun ActionsDialog(
    onDismiss: () -> Unit,
    onViewRecognition: () -> Unit,
    onUpdateRecognition: () -> Unit,
    onSaveRecognitions: () -> Unit,
    onLoadRecognitions: () -> Unit,
    onClearRecognitions: () -> Unit,
    onImportPhoto: () -> Unit,
    onHyperparameters: () -> Unit,
    onDeveloperMode: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Action:") },
        text = {
            Column {
                ActionItem("View Recognition List", onClick = onViewRecognition)
                ActionItem("Update Recognition List", onClick = onUpdateRecognition)
                ActionItem("Save Recognitions", onClick = onSaveRecognitions)
                ActionItem("Load Recognitions", onClick = onLoadRecognitions)
                ActionItem("Clear All Recognitions", onClick = onClearRecognitions)
                ActionItem("Import Photo (Beta)", onClick = onImportPhoto)
                ActionItem("Hyperparameters", onClick = onHyperparameters)
                ActionItem("Developer Mode", onClick = onDeveloperMode)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ActionItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        fontSize = 16.sp
    )
}

@Composable
fun AddFaceDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotEmpty()) {
                        onAdd(name)
                    }
                }
            ) {
                Text("ADD")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ViewRecognitionDialog(
    registered: HashMap<String, SimilarityClassifier.Recognition>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (registered.isEmpty()) "No Faces Added!!" else "Recognitions:")
        },
        text = {
            if (registered.isEmpty()) {
                Text("No recognitions available")
            } else {
                LazyColumn {
                    items(registered.keys.toList()) { name ->
                        Text(
                            text = name,
                            modifier = Modifier.padding(vertical = 4.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun UpdateRecognitionDialog(
    registered: HashMap<String, SimilarityClassifier.Recognition>,
    selectedNames: Set<String>,
    onSelectedNamesChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (registered.isEmpty()) "No Faces Added!!" else "Select Recognition to delete:"
            )
        },
        text = {
            if (registered.isEmpty()) {
                Text("No recognitions available")
            } else {
                LazyColumn {
                    items(registered.keys.toList()) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSelection = selectedNames.toMutableSet()
                                    if (newSelection.contains(name)) {
                                        newSelection.remove(name)
                                    } else {
                                        newSelection.add(name)
                                    }
                                    onSelectedNamesChanged(newSelection)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedNames.contains(name),
                                onCheckedChange = {
                                    val newSelection = selectedNames.toMutableSet()
                                    if (it) {
                                        newSelection.add(name)
                                    } else {
                                        newSelection.remove(name)
                                    }
                                    onSelectedNamesChanged(newSelection)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClearRecognitionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Do you want to delete all Recognitions?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HyperparameterSelectDialog(
    onDismiss: () -> Unit,
    onSelect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Hyperparameter:") },
        text = {
            ActionItem("Maximum Nearest Neighbour Distance", onClick = onSelect)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HyperparameterDialog(
    currentDistance: Float,
    onDismiss: () -> Unit,
    onUpdate: (Float) -> Unit
) {
    var distanceText by remember { mutableStateOf(currentDistance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Euclidean Distance") },
        text = {
            Column {
                Text(
                    "0.00 -> Perfect Match\n1.00 -> Default\nTurn On Developer Mode to find optimum value\n\nCurrent Value:"
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distance") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val newDistance = distanceText.toFloat()
                        onUpdate(newDistance)
                    } catch (e: NumberFormatException) {
                        // Invalid input, do nothing
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun readFromSP(context: Context): HashMap<String, SimilarityClassifier.Recognition> {
    val sharedPreferences = context.getSharedPreferences("HashMap", Context.MODE_PRIVATE)
    val defValue = Gson().toJson(HashMap<String, SimilarityClassifier.Recognition>())
    val json = sharedPreferences.getString("map", defValue) ?: defValue
    val token = object : TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {}
    val retrievedMap = Gson().fromJson<HashMap<String, SimilarityClassifier.Recognition>>(
        json,
        token.type
    ) ?: HashMap()

    val OUTPUT_SIZE = 192
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
    Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
    return retrievedMap
}

fun handleImageSelection(
    uri: Uri,
    context: Context,
    detector: FaceDetector,
    tfLite: Interpreter,
    registered: HashMap<String, SimilarityClassifier.Recognition>,
    flipX: Boolean,
    onFaceBitmapChanged: (Bitmap?) -> Unit,
    onRecognizeButtonTextChanged: (String) -> Unit,
    onAddFaceVisibleChanged: (Boolean) -> Unit,
    onRecoNameVisibleChanged: (Boolean) -> Unit,
    onFacePreviewVisibleChanged: (Boolean) -> Unit,
    onPreviewInfoChanged: (String) -> Unit,
    onTextAbovePreviewChanged: (String) -> Unit,
    onStartChanged: (Boolean) -> Unit,
    onShowAddFaceDialog: () -> Unit
) {
    try {
        val bitmap = getBitmapFromUri(context, uri)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    onRecognizeButtonTextChanged("Recognize")
                    onAddFaceVisibleChanged(true)
                    onRecoNameVisibleChanged(false)
                    onFacePreviewVisibleChanged(true)
                    onPreviewInfoChanged(
                        "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                    )
                    val face = faces[0]
                    var frame_bmp1 = rotateBitmap(bitmap, 0, flipX, false)
                    val boundingBox = RectF(face.boundingBox)
                    val cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox)
                    val scaled = getResizedBitmap(cropped_face, 112, 112)
                    onFaceBitmapChanged(scaled)
                    onShowAddFaceDialog()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show()
            }
        onFaceBitmapChanged(bitmap)
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

@Throws(IOException::class)
fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
    val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
    val fileDescriptor = parcelFileDescriptor?.fileDescriptor
    val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
    parcelFileDescriptor?.close()
    return image ?: throw IOException("Failed to decode bitmap from URI")
}

fun bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    camFace: Int,
    detector: FaceDetector,
    tfLite: Interpreter,
    registered: HashMap<String, SimilarityClassifier.Recognition>,
    start: Boolean,
    flipX: Boolean,
    distance: Float,
    developerMode: Boolean,
    intValues: IntArray,
    inputSize: Int,
    isModelQuantized: Boolean,
    IMAGE_MEAN: Float,
    IMAGE_STD: Float,
    OUTPUT_SIZE: Int,
    executor: Executor,
    onCameraSelectorReady: (CameraSelector) -> Unit,
    onEmbeedingsChanged: (Array<FloatArray>?) -> Unit,
    onFaceBitmapChanged: (Bitmap?) -> Unit,
    onRecoNameChanged: (String) -> Unit
): ImageAnalysis {
        Log.d("FaceRecognition", "[bindCamera] Starting. camFace=$camFace flipX=$flipX")
        Log.d("FaceRecognition", "[bindCamera] PreviewView: ${previewView.hashCode()}, parent: ${previewView.parent}")
        val preview = CameraPreview.Builder().build()
        Log.d("FaceRecognition", "[bindCamera] Preview created: ${preview.hashCode()}")
        preview.setSurfaceProvider(previewView.surfaceProvider)
        Log.d("FaceRecognition", "[bindCamera] Surface provider set")

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(camFace)
            .build()
    Log.d("FaceRecognition", "[bindCamera] CameraSelector created for camFace=$camFace")

    onCameraSelectorReady(cameraSelector)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        Log.d("FaceRecognition", "[bindCamera] ImageAnalysis created: ${imageAnalysis.hashCode()}")

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            try {
            Thread.sleep(0)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            @SuppressLint("UnsafeExperimentalUsageError")
            val mediaImage = imageProxy.image

            val image = if (mediaImage != null) {
                InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
            } else {
                null
            }

            if (image != null && mediaImage != null) {
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                        val face = faces[0]
                            val frame_bmp = toBitmap(mediaImage)
                            val rot = imageProxy.imageInfo.rotationDegrees
                            var frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false)
                            val boundingBox = RectF(face.boundingBox)
                            var cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox)

                            if (flipX)
                                cropped_face = rotateBitmap(cropped_face, 0, flipX, false)
                            val scaled = getResizedBitmap(cropped_face, 112, 112)

                        if (start) {
                            recognizeImage(
                                scaled,
                                tfLite,
                                registered,
                                distance,
                                developerMode,
                                intValues,
                                inputSize,
                                isModelQuantized,
                                IMAGE_MEAN,
                                IMAGE_STD,
                                OUTPUT_SIZE,
                                onEmbeedingsChanged,
                                onFaceBitmapChanged,
                                onRecoNameChanged
                            )
                        }
                        } else {
                            if (registered.isEmpty())
                            onRecoNameChanged("Add Face")
                        else
                            onRecoNameChanged("No Face Detected!")
                        }
                    }
                    .addOnFailureListener { e ->
                    Log.e("FaceRecognition", "analyze: face detection failure", e)
                    }
                    .addOnCompleteListener {
                    imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        Log.d("FaceRecognition", "[bindCamera] Calling bindToLifecycle...")
        Log.d("FaceRecognition", "[bindCamera] lifecycleOwner: ${lifecycleOwner.javaClass.simpleName}")
        Log.d("FaceRecognition", "[bindCamera] cameraSelector: $cameraSelector")
        Log.d("FaceRecognition", "[bindCamera] preview: ${preview.hashCode()}")
        Log.d("FaceRecognition", "[bindCamera] imageAnalysis: ${imageAnalysis.hashCode()}")
        cameraProvider.bindToLifecycle(
        lifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
        Log.d("FaceRecognition", "[bindCamera] bindToLifecycle completed successfully")
    
    return imageAnalysis
}

fun recognizeImage(
    bitmap: Bitmap,
    tfLite: Interpreter,
    registered: HashMap<String, SimilarityClassifier.Recognition>,
    distance: Float,
    developerMode: Boolean,
    intValues: IntArray,
    inputSize: Int,
    isModelQuantized: Boolean,
    IMAGE_MEAN: Float,
    IMAGE_STD: Float,
    OUTPUT_SIZE: Int,
    onEmbeedingsChanged: (Array<FloatArray>?) -> Unit,
    onFaceBitmapChanged: (Bitmap?) -> Unit,
    onRecoNameChanged: (String) -> Unit
) {
    onFaceBitmapChanged(bitmap)

        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())

        bitmap.getPixels(
        intValues,
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
            val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    imgData.put(((pixelValue shr 16) and 0xFF).toByte())
                    imgData.put(((pixelValue shr 8) and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
            } else {
                    imgData.putFloat((((pixelValue shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat((((pixelValue shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }

    val inputArray = arrayOf<Any>(imgData)
        val outputMap = HashMap<Int, Any>()
    val embeedings = Array(1) { FloatArray(OUTPUT_SIZE) }
    outputMap[0] = embeedings

    tfLite.runForMultipleInputsOutputs(inputArray, outputMap)
    onEmbeedingsChanged(embeedings)

        var distance_local = Float.MAX_VALUE
        var label = "?"

        if (registered.isNotEmpty()) {
        val nearest = findNearest(embeedings[0], registered)

            if (nearest.isNotEmpty() && nearest[0] != null) {
            val name = nearest[0].first
                distance_local = nearest[0].second

                if (developerMode) {
                    val secondNearest = if (nearest.size > 1) nearest[1] else Pair("N/A", Float.MAX_VALUE)
                if (distance_local < distance)
                    onRecoNameChanged(
                            "Nearest: $name\nDist: ${String.format("%.3f", distance_local)}\n2nd Nearest: ${secondNearest.first}\nDist: ${String.format("%.3f", secondNearest.second)}"
                    )
                    else
                    onRecoNameChanged(
                            "Unknown\nDist: ${String.format("%.3f", distance_local)}\nNearest: $name\nDist: ${String.format("%.3f", distance_local)}\n2nd Nearest: ${secondNearest.first}\nDist: ${String.format("%.3f", secondNearest.second)}"
                    )
                } else {
                if (distance_local < distance)
                    onRecoNameChanged(name)
                else
                    onRecoNameChanged("Unknown")
            }
        }
    }
}

private fun findNearest(
    emb: FloatArray,
    registered: HashMap<String, SimilarityClassifier.Recognition>
): List<Pair<String, Float>> {
        val neighbour_list = ArrayList<Pair<String, Float>>()
    var ret: Pair<String, Float>? = null
    var prev_ret: Pair<String, Float>? = null

        for ((name, recognition) in registered) {
            val extra = recognition.extra
            if (extra !is Array<*>) {
                continue
            }
            val storedEmbeddings = extra as? Array<FloatArray>
            if (storedEmbeddings == null || storedEmbeddings.isEmpty() || storedEmbeddings[0] == null) {
                continue
            }
            val knownEmb = storedEmbeddings[0]

            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = kotlin.math.sqrt(distance)

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

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)

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
        matrix.postRotate(rotationDegrees.toFloat())
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)

        var pos = 0

    if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
        var yBufferPos = -rowStride.toLong()
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

                return nv21
                }
            } catch (ex: ReadOnlyBufferException) {
            }

            vBuffer.put(1, savePixel)
        }

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

@Preview(showBackground = true, name = "MainScreen Preview")
@Composable
fun MainScreenPreview() {
    FaceRecognitionTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Camera Preview Placeholder
                Box(
                    modifier = Modifier
                        .width(297.dp)
                        .height(279.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera Preview",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Camera Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Camera switch button
                    FloatingActionButton(
                        onClick = { },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Switch Camera"
                        )
                    }
                }

                // Text above preview
                Text(
                    text = "Recognized Face:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // Face preview or Recognition name
                Box(
                    modifier = Modifier
                        .width(203.dp)
                        .height(200.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add Face",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Face")
                    }

                    Button(
                        onClick = { },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ACTIONS")
                    }
                }
            }
        }
    }
}
