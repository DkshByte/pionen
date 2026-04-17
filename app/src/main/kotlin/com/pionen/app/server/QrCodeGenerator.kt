package com.pionen.app.server

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates QR codes for easy URL/token sharing.
 */
object QrCodeGenerator {
    
    /**
     * Generate a QR code bitmap for the given content.
     * 
     * @param content The text to encode in the QR code
     * @param size Width and height of the QR code in pixels
     * @param backgroundColor Background color (default white)
     * @param foregroundColor Foreground/code color (default black)
     */
    fun generate(
        content: String,
        size: Int = 512,
        backgroundColor: Int = Color.WHITE,
        foregroundColor: Int = Color.BLACK
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) foregroundColor else backgroundColor
                    )
                }
            }
            
            bitmap
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Generate a QR code containing the server URL with embedded token.
     */
    fun generateServerQr(
        serverInfo: SecureWebServer.ServerInfo,
        size: Int = 512
    ): Bitmap? {
        // SECURITY: Only encode the URL — never embed the access token.
        // The token must be entered manually on the web login page to prevent
        // leakage via browser history, Referer headers, and shoulder-surfing.
        return generate(
            content = serverInfo.url,
            size = size,
            backgroundColor = 0xFF121215.toInt(),  // Dark background
            foregroundColor = 0xFF4ade80.toInt()   // VaultGreen
        )
    }
}
