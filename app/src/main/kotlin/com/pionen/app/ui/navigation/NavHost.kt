package com.pionen.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pionen.app.ui.screens.CameraScreen
import com.pionen.app.ui.screens.DownloadScreen
import com.pionen.app.ui.screens.FileViewerScreen
import com.pionen.app.ui.screens.GalleryScreen
import com.pionen.app.ui.screens.LockScreen
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
    isLocked: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (isLocked) Screen.Lock.route else Screen.Vault.route
    ) {
        composable(Screen.Lock.route) {
            LockScreen(
                onUnlocked = {
                    navController.navigate(Screen.Vault.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Vault.route) {
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
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
