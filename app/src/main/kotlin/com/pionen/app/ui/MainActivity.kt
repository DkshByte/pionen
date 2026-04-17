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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.pionen.app.core.CompatibilityChecker
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
import kotlinx.coroutines.isActive
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

    @Inject
    lateinit var compatibilityChecker: CompatibilityChecker

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

        // Run compatibility check synchronously — it's fast (key gen) and must
        // block the UI before anything security-related is shown.
        val compatResult = compatibilityChecker.check()
        
        if (panicManager.detectTampering()) {
            setContent {
                PionenTheme {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        com.pionen.app.ui.screens.IncompatibleDeviceScreen(
                            failedReasons = listOf("Security Lockdown: Host OS represents a compromised environment (Root/Debugger active).")
                        )
                    }
                }
            }
            return
        }
        
        setContent {
            PionenTheme {
                val lockState by lockManager.lockState.collectAsState()
                val navController = rememberNavController()
                val shakeTriggered by shakeDetector.shakeTriggered.collectAsState()
                
                var initialRoute by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(compatResult) {
                    if (compatResult.isCompatible) {
                        val configured = lockManager.hasPinConfigured()
                        initialRoute = if (configured) Screen.Lock.route else Screen.Setup.route
                    } else {
                        initialRoute = Screen.Incompatible.route
                    }
                }
                
                // Enforce lock state across the entire app
                LaunchedEffect(lockState, initialRoute) {
                    if (initialRoute == null) return@LaunchedEffect
                    
                    if (lockState is LockState.Locked && compatResult.isCompatible) {
                        val currentRoute = navController.currentDestination?.route
                        if (currentRoute != Screen.Lock.route && currentRoute != Screen.Incompatible.route && currentRoute != Screen.Setup.route) {
                            navController.navigate(Screen.Lock.route) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                
                // Periodic auto-lock check
                LaunchedEffect(Unit) {
                    while (isActive) {
                        lockManager.checkInactivityLock()
                        delay(15_000) // Check every 15 seconds
                    }
                }
                
                // Handle shake-to-wipe — navigate to confirmation screen, NEVER instant-wipe
                LaunchedEffect(shakeTriggered) {
                    if (shakeTriggered && compatResult.isCompatible) {
                        val currentRoute = navController.currentDestination?.route
                        // Only trigger if vault is open (not on lock/setup) and not already confirming
                        if (currentRoute != Screen.Lock.route && 
                            currentRoute != Screen.Setup.route &&
                            currentRoute != Screen.PanicConfirm.route) {
                            navController.navigate(Screen.PanicConfirm.route) {
                                launchSingleTop = true
                            }
                        }
                        shakeDetector.resetTrigger()
                    }
                }
                
                if (initialRoute != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PionenNavHost(
                            navController = navController,
                            startDestination = initialRoute!!,
                            incompatibleReasons = compatResult.failedReasons,
                            onNavigatingToVault = { isNavigatingAfterUnlock = true },
                        onVaultSettled = { isNavigatingAfterUnlock = false }
                    )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(com.pionen.app.ui.theme.DarkBackground))
                }
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
