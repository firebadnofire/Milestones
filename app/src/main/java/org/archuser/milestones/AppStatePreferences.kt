package org.archuser.milestones

import android.content.Context
import androidx.core.content.edit

object AppStatePreferences {
    private const val PREFS_NAME = "milestones_prefs"
    private const val PREFS_KEY_STATE = "milestone_entries"
    private const val PREFS_KEY_MATERIAL_YOU = "material_you_enabled"
    private const val PREFS_KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

    fun load(context: Context): AppState {
        val payload = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY_STATE, null)
            ?: return AppState()
        return AppStateStorage.decode(payload)
    }

    fun save(context: Context, appState: AppState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(PREFS_KEY_STATE, AppStateStorage.encode(appState))
        }
    }

    fun isMaterialYouEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREFS_KEY_MATERIAL_YOU, false)
    }

    fun setMaterialYouEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(PREFS_KEY_MATERIAL_YOU, enabled)
        }
    }

    fun hasRequestedNotificationPermission(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREFS_KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
    }

    fun setNotificationPermissionRequested(context: Context, requested: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(PREFS_KEY_NOTIFICATION_PERMISSION_REQUESTED, requested)
        }
    }
}
