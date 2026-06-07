package org.archuser.milestones

import org.json.JSONArray
import org.json.JSONObject

object MedicineStorage {
    private const val MINUTES_PER_DAY = 24 * 60

    fun encodeArray(medicines: List<Medicine>): JSONArray {
        val array = JSONArray()
        medicines.forEach { medicine ->
            array.put(
                JSONObject()
                    .put("id", medicine.id)
                    .put("name", medicine.name)
                    .put("scheduledTimes", JSONArray(medicine.scheduledTimes))
                    .put("scheduledWeekdays", JSONArray(medicine.scheduledWeekdays))
                    .put("doseLogs", encodeDoseLogs(medicine.doseLogs))
            )
        }
        return array
    }

    fun decodeArray(array: JSONArray): List<Medicine> {
        val decoded = buildList {
            for (index in 0 until array.length()) {
                add(decodeMedicine(array.getJSONObject(index)))
            }
        }
        require(decoded.distinctBy(Medicine::id).size == decoded.size) {
            "Medicine IDs must be unique."
        }
        return decoded
    }

    private fun decodeMedicine(item: JSONObject): Medicine {
        val name = requireString(item, "name").trim()
        require(name.isNotBlank()) {
            "Medicine name must not be blank."
        }

        val scheduledTimes = decodeScheduledTimes(item.optJSONArray("scheduledTimes"))
        val scheduledWeekdays = decodeScheduledWeekdays(item.optJSONArray("scheduledWeekdays"))
        val doseLogs = decodeDoseLogs(item.optJSONArray("doseLogs"), scheduledTimes.size)

        return Medicine(
            id = requireLong(item, "id"),
            name = name,
            scheduledTimes = scheduledTimes,
            scheduledWeekdays = scheduledWeekdays,
            doseLogs = doseLogs
        )
    }

    private fun decodeScheduledTimes(array: JSONArray?): List<Int> {
        require(array != null && array.length() > 0) {
            "Medicines must include at least one scheduled time."
        }

        val scheduledTimes = buildList {
            for (index in 0 until array.length()) {
                val minutes = array.getInt(index)
                require(minutes in 0 until MINUTES_PER_DAY) {
                    "Scheduled times must be between 0 and 1439 minutes."
                }
                add(minutes)
            }
        }.sorted()

        require(scheduledTimes.distinct().size == scheduledTimes.size) {
            "Scheduled times must be unique."
        }

        return scheduledTimes
    }

    private fun decodeScheduledWeekdays(array: JSONArray?): List<Int> {
        if (array == null) {
            return Medicine.ALL_SCHEDULED_WEEKDAYS
        }

        val scheduledWeekdays = buildList {
            for (index in 0 until array.length()) {
                val dayOfWeek = array.getInt(index)
                require(dayOfWeek in Medicine.ALL_SCHEDULED_WEEKDAYS) {
                    "Scheduled weekdays must be valid Calendar day-of-week values."
                }
                add(dayOfWeek)
            }
        }

        require(scheduledWeekdays.isNotEmpty()) {
            "Medicines must include at least one scheduled weekday."
        }
        require(scheduledWeekdays.distinct().size == scheduledWeekdays.size) {
            "Scheduled weekdays must be unique."
        }

        return Medicine.ALL_SCHEDULED_WEEKDAYS.filter { it in scheduledWeekdays }
    }

    private fun encodeDoseLogs(doseLogs: List<MedicineDoseLog>): JSONArray {
        val array = JSONArray()
        doseLogs.forEach { log ->
            array.put(
                JSONObject()
                    .put("localDay", log.localDay)
                    .put("scheduledDoseIndex", log.scheduledDoseIndex)
            )
        }
        return array
    }

    private fun decodeDoseLogs(array: JSONArray?, doseCount: Int): List<MedicineDoseLog> {
        if (array == null) return emptyList()

        val decoded = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val localDay = requireString(item, "localDay")
                LocalDay.parse(localDay)
                val scheduledDoseIndex = item.getInt("scheduledDoseIndex")
                require(scheduledDoseIndex in 0 until doseCount) {
                    "Dose log index must match the medicine schedule."
                }
                add(
                    MedicineDoseLog(
                        localDay = localDay,
                        scheduledDoseIndex = scheduledDoseIndex
                    )
                )
            }
        }

        require(decoded.distinct().size == decoded.size) {
            "Dose logs must be unique."
        }

        return decoded.sortedWith(compareBy({ it.localDay }, { it.scheduledDoseIndex }))
    }

    private fun requireLong(item: JSONObject, key: String): Long {
        require(item.has(key) && !item.isNull(key)) {
            "Missing $key."
        }
        return item.getLong(key)
    }

    private fun requireString(item: JSONObject, key: String): String {
        require(item.has(key) && !item.isNull(key)) {
            "Missing $key."
        }
        return item.getString(key)
    }
}
