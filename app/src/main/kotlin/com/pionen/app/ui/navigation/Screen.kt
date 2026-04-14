package com.pionen.app.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Lock : Screen("lock")
    object Vault : Screen("vault")
    object FileViewer : Screen("viewer/{fileId}") {
        fun createRoute(fileId: String) = "viewer/$fileId"
    }
    object Gallery : Screen("gallery/{startIndex}") {
        fun createRoute(startIndex: Int) = "gallery/$startIndex"
    }
    object Camera : Screen("camera")
    object Browser : Screen("browser")
    object Download : Screen("download")
    object Settings : Screen("settings")
    object PanicConfirm : Screen("panic_confirm")
}

