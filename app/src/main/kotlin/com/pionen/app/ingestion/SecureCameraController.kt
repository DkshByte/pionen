package com.pionen.app.ingestion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.core.vault.VaultFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * SecureCameraController: Captures photos directly into the encrypted vault.
 * 
 * Security Design:
 * - Camera output encrypted BEFORE any disk write
 * - Bypasses system gallery/cache entirely
 * - No plaintext image files created
 * - Uses CameraX for reliable capture
 */
@Singleton
class SecureCameraController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultEngine: VaultEngine
) {
    
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    
    companion object {
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
        private const val JPEG_QUALITY = 95
    }
    
    /**
     * Initialize the camera with the given lifecycle owner.
     * Must be called before capturing photos.
     * 
     * @param lifecycleOwner The lifecycle owner for camera binding
     * @param cameraSelector Which camera to use (default: back)
     * @return The ImageCapture use case for preview binding
     */
    suspend fun initialize(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    ): ImageCapture = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Build ImageCapture use case
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(android.view.Surface.ROTATION_0)
                    .build()
                
                // We don't bind preview here - that's done in the UI layer
                // This just prepares the ImageCapture
                
                continuation.resume(imageCapture!!)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Capture a photo and return raw JPEG bytes securely.
     */
    suspend fun captureToByteArray(): ByteArray {
        val capture = imageCapture
            ?: throw IllegalStateException("Camera not initialized. Call initialize() first.")
        
        val image = suspendCancellableCoroutine<ImageProxy> { continuation ->
            val executor: Executor = ContextCompat.getMainExecutor(context)
            
            capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    continuation.resume(image)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            })
        }
        
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            val rotationDegrees = image.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.width, bitmap.height,
                    matrix, true
                )
            }
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            bitmap.recycle()
            
            outputStream.toByteArray()
        } finally {
            image.close()
        }
    }

    /**
     * Capture a photo directly into the encrypted vault.
     * 
     * @param fileName Optional custom filename (auto-generated if not provided)
     * @return The captured and encrypted VaultFile
     */
    suspend fun captureToVault(
        fileName: String? = null
    ): VaultFile {
        val jpegBytes = captureToByteArray()
        
        val actualFileName = fileName ?: run {
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
            "IMG_$timestamp.jpg"
        }
        
        return vaultEngine.createFile(
            content = jpegBytes,
            fileName = actualFileName,
            mimeType = "image/jpeg"
        )
    }
    
    /**
     * Switch between front and back cameras.
     */
    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector
    ) {
        cameraProvider?.let { provider ->
            provider.unbindAll()
            imageCapture?.let {
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, it)
            }
        }
    }
    
    /**
     * Release camera resources.
     */
    fun release() {
        cameraProvider?.unbindAll()
        imageCapture = null
        cameraProvider = null
    }
}

/**
 * Camera capture result.
 */
sealed class CaptureResult {
    data class Success(val file: VaultFile) : CaptureResult()
    data class Error(val message: String, val exception: Exception? = null) : CaptureResult()
}
