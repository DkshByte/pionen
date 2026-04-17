package com.pionen.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pionen.app.ui.screens.CameraScreen
import com.pionen.app.ui.screens.DownloadScreen
import com.pionen.app.ui.screens.FileViewerScreen
import com.pionen.app.ui.screens.GalleryScreen
import com.pionen.app.ui.screens.IncompatibleDeviceScreen
import com.pionen.app.ui.screens.LockScreen
import com.pionen.app.ui.screens.SetupScreen
import com.pionen.app.ui.screens.PanicConfirmScreen
import com.pionen.app.ui.screens.SecureBrowserScreen
import com.pionen.app.ui.screens.SettingsScreen
import com.pionen.app.ui.screens.VaultScreen

/**
 * Main navigation graph for the app.
 */
@Composable
fun PionenNavHost(
    navController: NavHostController,
    startDestination: String,
    incompatibleReasons: List<String> = emptyList(),
    onNavigatingToVault: () -> Unit = {},
    onVaultSettled: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Incompatible.route) {
            IncompatibleDeviceScreen(failedReasons = incompatibleReasons)
        }
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    try {
                        onNavigatingToVault()
                        navController.navigate(Screen.Vault.route) {
                            popUpTo(0) { inclusive = true } // Clear entire backstack on fresh install
                            launchSingleTop = true
                        }
                    } catch (e: Exception) { }
                }
            )
        }
        composable(Screen.Lock.route) {
            LockScreen(
                onUnlocked = {
                    try {
                        onNavigatingToVault()
                        navController.navigate(Screen.Vault.route) {
                            popUpTo(Screen.Lock.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    } catch (e: Exception) {
                        // Navigation safety net
                    }
                }
            )
        }
        
        composable(Screen.Vault.route) {
            LaunchedEffect(Unit) {
                onVaultSettled()
            }
            VaultScreen(
                onFileClick = { fileId ->
                    navController.navigate(Screen.FileViewer.createRoute(fileId.toString()))
                },
                onGalleryClick = { startIndex ->
                    navController.navigate(Screen.Gallery.createRoute(startIndex))
                },
                onCameraClick = {
                    navController.navigate(Screen.Camera.route)
                },
                onBrowserClick = {
                    navController.navigate(Screen.Browser.route)
                },
                onDownloadClick = {
                    navController.navigate(Screen.Download.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.FileViewer.route,
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId") ?: return@composable
            FileViewerScreen(
                fileId = fileId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Gallery.route,
            arguments = listOf(
                navArgument("startIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            GalleryScreen(
                startIndex = startIndex,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Camera.route) {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onFileCaptured = { fileId ->
                    navController.popBackStack()
                    navController.navigate(Screen.FileViewer.createRoute(fileId.toString()))
                }
            )
        }
        
        composable(Screen.Browser.route) {
            SecureBrowserScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Download.route) {
            DownloadScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPanicWipe = {
                    navController.navigate(Screen.PanicConfirm.route)
                }
            )
        }
        
        composable(Screen.PanicConfirm.route) {
            PanicConfirmScreen(
                onCancel = { navController.popBackStack() },
                onWipeComplete = {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Vault.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
