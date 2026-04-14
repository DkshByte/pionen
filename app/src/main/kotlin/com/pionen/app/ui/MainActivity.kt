package com.pionen.app.ui

import android.os.Bundle
import android.os.Process
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.pionen.app.core.security.LockManager
import com.pionen.app.core.security.LockState
import com.pionen.app.core.security.PanicManager
import com.pionen.app.core.security.ScreenshotShield
import com.pionen.app.core.security.ShakeDetector
import com.pionen.app.ui.navigation.PionenNavHost
import com.pionen.app.ui.navigation.Screen
import com.pionen.app.ui.theme.PionenTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Activity with FLAG_SECURE, auto-lock on background, and shake-to-wipe.
 * Extends FragmentActivity for biometric authentication support.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var lockManager: LockManager
    
    @Inject
    lateinit var screenshotShield: ScreenshotShield
    
    @Inject
    lateinit var shakeDetector: ShakeDetector
    
    @Inject
    lateinit var panicManager: PanicManager

    /**
     * Guard against onStop re-locking the vault during the very first
     * navigation away from the lock screen. Set to true right before we
     * navigate to the vault, cleared once the vault composable is settled.
     */
    private var isNavigatingAfterUnlock = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply screenshot protection
        screenshotShield.protect(this)
        
        // Start shake detection - inject lockManager so it won't fire during lock screen
        shakeDetector.lockManager = lockManager
        shakeDetector.start()
        
        setContent {
            PionenTheme {
                val lockState by lockManager.lockState.collectAsState()
                val navController = rememberNavController()
                val shakeTriggered by shakeDetector.shakeTriggered.collectAsState()
                
                // Handle shake-to-wipe
                LaunchedEffect(shakeTriggered) {
                    if (shakeTriggered) {
                        // Trigger panic wipe
                        panicManager.executePanicWipe()
                        shakeDetector.resetTrigger()
                        
                        // Short delay for wipe to complete
                        delay(500)
                        
                        // Force close the app
                        finishAffinity()
                        Process.killProcess(Process.myPid())
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PionenNavHost(
                        navController = navController,
                        startDestination = Screen.Lock.route,
                        onNavigatingToVault = { isNavigatingAfterUnlock = true },
                        onVaultSettled = { isNavigatingAfterUnlock = false }
                    )
                }
            }
        }
        
        // Monitor lifecycle for auto-lock
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Called when app comes to foreground
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Do NOT re-lock if we're in the middle of navigating from lock -> vault.
        // The biometric prompt also causes onStop; that's already guarded inside
        // LockManager via isBiometricPromptShowing.
        if (!isNavigatingAfterUnlock) {
            lockManager.onAppBackgrounded()
        }
    }
    
    override fun onStart() {
        super.onStart()
        lockManager.onAppForegrounded()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        shakeDetector.stop()
    }
}
