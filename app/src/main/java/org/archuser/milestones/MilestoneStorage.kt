package org.archuser.milestones

import org.json.JSONArray
import org.json.JSONObject

object MilestoneStorage {
    fun encode(milestones: List<Milestone>): String {
        return encodeArray(milestones).toString()
    }

    fun encodeArray(milestones: List<Milestone>): JSONArray {
        val array = JSONArray()
        milestones.forEach { milestone ->
            val item = JSONObject()
                .put("id", milestone.id)
                .put("name", milestone.name)
                .put("startDateMillis", milestone.startDateMillis)
                .put("resetHistory", JSONArray(milestone.resetHistory))
            array.put(item)
        }
        return array
    }

    fun decode(payload: String): List<Milestone> {
        return decodeArray(JSONArray(payload))
    }

    fun decodeArray(array: JSONArray): List<Milestone> {
        val decoded = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val name = item.getString("name").trim()
                require(name.isNotBlank()) {
                    "Milestone name must not be blank."
                }
                val startDateMillis = LocalDay.fromTimestamp(item.getLong("startDateMillis"))
                    .startOfDayMillis()
                val resetHistory = decodeResetHistory(item.optJSONArray("resetHistory"))
                add(
                    Milestone(
                        id = item.getLong("id"),
                        name = name,
                        startDateMillis = startDateMillis,
                        resetHistory = resetHistory
                    )
                )
            }
        }
        require(decoded.distinctBy(Milestone::id).size == decoded.size) {
            "Milestone IDs must be unique."
        }
        return decoded
    }

    private fun decodeResetHistory(array: JSONArray?): List<String> {
        if (array == null) return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val localDay = array.getString(index)
                LocalDay.parse(localDay)
                add(localDay)
            }
        }
    }
}
