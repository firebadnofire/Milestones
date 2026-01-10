package org.archuser.milestones

import org.json.JSONArray
import org.json.JSONObject

object MilestoneStorage {
    fun encode(milestones: List<Milestone>): String {
        val array = JSONArray()
        milestones.forEach { milestone ->
            val item = JSONObject()
                .put("id", milestone.id)
                .put("name", milestone.name)
                .put("startDateMillis", milestone.startDateMillis)
            array.put(item)
        }
        return array.toString()
    }

    fun decode(payload: String): List<Milestone> {
        val array = JSONArray(payload)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Milestone(
                        id = item.getLong("id"),
                        name = item.getString("name"),
                        startDateMillis = item.getLong("startDateMillis")
                    )
                )
            }
        }
    }
}
