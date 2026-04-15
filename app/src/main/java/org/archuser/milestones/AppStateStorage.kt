package org.archuser.milestones

import org.json.JSONArray
import org.json.JSONObject

object AppStateStorage {
    const val CURRENT_VERSION = 1

    fun encode(appState: AppState): String {
        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("milestones", MilestoneStorage.encodeArray(appState.milestones))
            .put("medicines", MedicineStorage.encodeArray(appState.medicines))
            .toString()
    }

    fun decode(payload: String): AppState {
        val trimmedPayload = payload.trim()
        require(trimmedPayload.isNotBlank()) {
            "App state payload must not be blank."
        }

        return if (trimmedPayload.startsWith("[")) {
            AppState(
                version = CURRENT_VERSION,
                milestones = MilestoneStorage.decodeArray(JSONArray(trimmedPayload)),
                medicines = emptyList()
            )
        } else {
            decodeRootObject(JSONObject(trimmedPayload))
        }
    }

    private fun decodeRootObject(root: JSONObject): AppState {
        val version = if (root.has("version")) root.getInt("version") else CURRENT_VERSION
        require(version == CURRENT_VERSION) {
            "Unsupported app state version: $version"
        }

        val milestones = MilestoneStorage.decodeArray(root.optJSONArray("milestones") ?: JSONArray())
        val medicines = MedicineStorage.decodeArray(root.optJSONArray("medicines") ?: JSONArray())

        return AppState(
            version = CURRENT_VERSION,
            milestones = milestones,
            medicines = medicines
        )
    }
}
