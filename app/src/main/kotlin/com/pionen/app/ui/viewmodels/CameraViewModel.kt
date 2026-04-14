package com.pionen.app.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.vault.VaultEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * CameraViewModel that handles capture directly using the bound ImageCapture.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultEngine: VaultEngine
) : ViewModel() {
    
    companion object {
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
        private const val JPEG_QUALITY = 95
    }
    
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    // The ImageCapture that gets bound to the camera
    private var imageCapture: ImageCapture? = null
    
    /**
     * Get or create the ImageCapture use case.
     * This is bound to the camera in CameraScreen.
     */
    fun getImageCapture(): ImageCapture {
        if (imageCapture == null) {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
        }
        return imageCapture!!
    }
    
    /**
     * Capture a photo using the bound ImageCapture and encrypt directly to vault.
     */
    fun capturePhoto(onSuccess: (UUID) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            _error.value = "Camera not ready. Please wait."
            return
        }
        
        _isCapturing.value = true
        _error.value = null
        
        val executor = ContextCompat.getMainExecutor(context)
        
        capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                viewModelScope.launch {
                    try {
                        // Convert ImageProxy to JPEG bytes
                        val jpegBytes = processImage(image)
                        
                        // Generate filename
                        val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
                        val fileName = "IMG_$timestamp.jpg"
                        
                        // Encrypt and store in vault
                        val vaultFile = vaultEngine.createFile(
                            content = jpegBytes,
                            fileName = fileName,
                            mimeType = "image/jpeg"
                        )
                        
                        _isCapturing.value = false
                        onSuccess(vaultFile.id)
                        
                    } catch (e: Exception) {
                        _error.value = e.message ?: "Failed to save photo"
                        _isCapturing.value = false
                    } finally {
                        image.close()
                    }
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                _error.value = exception.message ?: "Capture failed"
                _isCapturing.value = false
            }
        })
    }
    
    /**
     * Process ImageProxy to JPEG byte array with rotation correction.
     */
    private fun processImage(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        // Decode to Bitmap
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        // Apply rotation if needed
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
        
        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val jpegBytes = outputStream.toByteArray()
        
        // Clean up
        bitmap.recycle()
        
        return jpegBytes
    }
    
    override fun onCleared() {
        super.onCleared()
        imageCapture = null
    }
}
