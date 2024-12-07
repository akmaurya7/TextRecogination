package com.example.textrecogination

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                hasCameraPermission = isGranted
            }

        // Check for permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            hasCameraPermission = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            if (hasCameraPermission) {
                TextRecognitionApp()
            } else {
                PermissionDeniedScreen {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera permission is required to use this app.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun TextRecognitionApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var isCameraInitialized by remember { mutableStateOf(false) }
    var isImageCaptured by remember { mutableStateOf(false) }
    var showCaptureButtons by remember { mutableStateOf(true) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Initialize the CameraX
    LaunchedEffect(Unit) {
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                imageCapture = startCamera(
                    cameraProvider = cameraProvider,
                    lifecycleOwner = lifecycleOwner,
                    context = context,
                    cameraExecutor = cameraExecutor
                )
                isCameraInitialized = true
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    // UI Layout
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Text Recognition",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (capturedBitmap != null && isImageCaptured) {
                // Display the captured image
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                // Display buttons to confirm or retake
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        // Retake the picture
                        isImageCaptured = false
                        showCaptureButtons = true
                    }) {
                        Text("Retake")
                    }

                    Button(onClick = {
                        // Confirm and process the image
                        processTextRecognition(capturedBitmap!!, context) { text ->
                            recognizedText = text
                        }
                    }) {
                        Text("Confirm")
                    }
                }
            } else if (showCaptureButtons) {
                // Capture Image Button
                Button(
                    onClick = {
                        imageCapture?.let { captureImage ->
                            capturePhoto(
                                imageCapture = captureImage,
                                context = context,
                                onImageCaptured = { bitmap ->
                                    capturedBitmap = bitmap
                                    isImageCaptured = true
                                    showCaptureButtons = false
                                }
                            )
                        }
                    },
                    enabled = isCameraInitialized
                ) {
                    Text(text = "Capture Image")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display recognized text
            Text(
                text = recognizedText.ifEmpty { "Recognized text will appear here." },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// Helper function to start CameraX
private fun startCamera(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    context: android.content.Context,
    cameraExecutor: ExecutorService
): ImageCapture {
    val imageCapture = ImageCapture.Builder().build()

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        imageCapture
    )

    return imageCapture
}

// Helper function to capture an image
private fun capturePhoto(
    imageCapture: ImageCapture,
    context: android.content.Context,
    onImageCaptured: (Bitmap) -> Unit
) {
    val outputFile = File(context.externalCacheDir, "captured_image.jpg")

    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(outputFile).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                onImageCaptured(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("TextRecognitionApp", "Image capture failed: ${exception.message}", exception)
            }
        }
    )
}

// Helper function to process text recognition
private fun processTextRecognition(bitmap: Bitmap, context: android.content.Context, onTextRecognized: (String) -> Unit) {
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
            onTextRecognized(visionText.text)
        }
        .addOnFailureListener { e ->
            onTextRecognized("Error: ${e.message}")
        }
}
