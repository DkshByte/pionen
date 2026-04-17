package com.pionen.app.core.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.stealthStore: DataStore<Preferences> by preferencesDataStore(name = "stealth_prefs")

/**
 * StealthManager: Switch app icon and name to disguise the app.
 * 
 * Available disguises:
 * - Default: Pionen (lock icon)
 * - Calculator: "Calculator" with calculator icon
 * - Notes: "Quick Notes" with notes icon
 * - Utilities: "System Utilities" with settings icon
 */
@Singleton
class StealthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val CURRENT_DISGUISE_KEY = stringPreferencesKey("current_disguise")
        
        // Activity component names - must match manifest aliases
        const val ALIAS_DEFAULT = "com.pionen.app.ui.MainActivity"
        const val ALIAS_CALCULATOR = "com.pionen.app.ui.CalculatorLauncher"
        const val ALIAS_NOTES = "com.pionen.app.ui.NotesLauncher"
        const val ALIAS_UTILITIES = "com.pionen.app.ui.UtilitiesLauncher"
    }
    
    /**
     * Available app disguises.
     */
    enum class Disguise(
        val alias: String,
        val displayName: String,
        val description: String
    ) {
        DEFAULT(ALIAS_DEFAULT, "Pionen", "Default secure vault icon"),
        CALCULATOR(ALIAS_CALCULATOR, "Calculator", "Disguise as calculator app"),
        NOTES(ALIAS_NOTES, "Quick Notes", "Disguise as notes app"),
        UTILITIES(ALIAS_UTILITIES, "System Utilities", "Disguise as system utility")
    }
    
    /**
     * Get current disguise as Flow.
     */
    val currentDisguise: Flow<Disguise> = context.stealthStore.data.map { prefs ->
        val aliasName = prefs[CURRENT_DISGUISE_KEY] ?: ALIAS_DEFAULT
        Disguise.entries.find { it.alias == aliasName } ?: Disguise.DEFAULT
    }
    
    /**
     * Switch to a new disguise.
     * This changes the app icon and name on the home screen.
     */
    suspend fun switchDisguise(newDisguise: Disguise) {
        val currentAlias = currentDisguise.first().alias
        
        // Skip if already using this disguise
        if (currentAlias == newDisguise.alias) return
        
        val packageManager = context.packageManager
        
        // Disable current alias
        setComponentEnabled(packageManager, currentAlias, false)
        
        // Enable new alias
        setComponentEnabled(packageManager, newDisguise.alias, true)
        
        // Save preference
        context.stealthStore.edit { prefs ->
            prefs[CURRENT_DISGUISE_KEY] = newDisguise.alias
        }
    }
    
    private fun setComponentEnabled(pm: PackageManager, componentName: String, enabled: Boolean) {
        val component = ComponentName(context.packageName, componentName)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        
        try {
            pm.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            // May fail if alias doesn't exist
        }
    }
    
    /**
     * Get all available disguises.
     */
    fun getAvailableDisguises(): List<Disguise> = Disguise.entries
}
