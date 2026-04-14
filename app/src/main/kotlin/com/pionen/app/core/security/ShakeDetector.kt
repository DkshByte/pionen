package com.pionen.app.core.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * ShakeDetector: Detects phone shaking to trigger emergency panic wipe.
 * 
 * Security Design:
 * - Shake phone vigorously to trigger instant wipe
 * - Requires sustained shaking to prevent accidental triggers
 * - Can be enabled/disabled in settings
 */
@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {
    
    companion object {
        // Shake detection thresholds — raised to avoid false positives during normal handling
        private const val SHAKE_THRESHOLD_GRAVITY = 3.2f  // Requires vigorous shake
        private const val SHAKE_COUNT_RESET_TIME_MS = 2000L  // Reset after 2 seconds
        private const val MIN_SHAKES_TO_TRIGGER = 8  // Requires 8 rapid shakes in 2 seconds
    }
    
    private val sensorManager: SensorManager? = 
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = 
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0
    private var isEnabled: Boolean = false

    // Injected so the shake detector can refuse to fire while vault is locked
    var lockManager: LockManager? = null

    private val _shakeTriggered = MutableStateFlow(false)
    val shakeTriggered: StateFlow<Boolean> = _shakeTriggered
    
    /**
     * Start listening for shakes.
     */
    fun start() {
        if (accelerometer == null) return
        isEnabled = true
        sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }
    
    /**
     * Stop listening for shakes.
     */
    fun stop() {
        isEnabled = false
        sensorManager?.unregisterListener(this)
        shakeCount = 0
    }
    
    /**
     * Reset the trigger state.
     */
    fun resetTrigger() {
        _shakeTriggered.value = false
        shakeCount = 0
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // Calculate acceleration magnitude (excluding gravity approximation)
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        
        // gForce will be close to 1 when there is no movement
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        
        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            
            // Reset shake count if too much time passed
            if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }
            
            shakeTimestamp = now
            shakeCount++

            // Check if we've reached the threshold
            if (shakeCount >= MIN_SHAKES_TO_TRIGGER) {
                shakeCount = 0
                // SAFETY: Never trigger panic while vault is locked (user could be
                // entering their password, or the phone is just in a pocket).
                val isVaultLocked = lockManager?.lockState?.value is LockState.Locked
                if (!isVaultLocked) {
                    _shakeTriggered.value = true
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}
