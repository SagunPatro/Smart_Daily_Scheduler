package com.smartscheduler.app.data.session

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.smartscheduler.app.data.model.ScheduleResponseDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages active schedule session lifecycle and expiration.
 * Automatically resets the schedule after the day end time or when a new day arrives,
 * so the user can easily input new day timings and durations newly for the new day
 * without having to manually clear or delete yesterday's timetable.
 */
class ScheduleSessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveActiveSchedule(schedule: ScheduleResponseDto, dayStart: Int, dayEnd: Int) {
        val today = getTodayDateString()
        prefs.edit()
            .putString(KEY_ACTIVE_SCHEDULE_JSON, gson.toJson(schedule))
            .putString(KEY_SCHEDULE_DATE, today)
            .putInt(KEY_DAY_START, dayStart)
            .putInt(KEY_DAY_END, dayEnd)
            .putLong(KEY_SAVED_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getActiveSchedule(): ScheduleResponseDto? {
        if (isScheduleExpired()) {
            clearActiveSchedule()
            return null
        }
        val json = prefs.getString(KEY_ACTIVE_SCHEDULE_JSON, null) ?: return null
        return try {
            gson.fromJson(json, ScheduleResponseDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getSavedDayStart(): Int = prefs.getInt(KEY_DAY_START, 540)
    fun getSavedDayEnd(): Int = prefs.getInt(KEY_DAY_END, 1380)
    fun getActiveScheduleDate(): String? = prefs.getString(KEY_SCHEDULE_DATE, null)

    /**
     * Checks if the active schedule has expired:
     * 1. If created on a previous calendar day (e.g. yesterday), it has expired.
     * 2. If created today, but current time >= dayEnd (e.g. 23:00 / 11:00 PM), it has expired.
     */
    fun isScheduleExpired(): Boolean {
        val scheduleDate = prefs.getString(KEY_SCHEDULE_DATE, null) ?: return false
        val dayEnd = prefs.getInt(KEY_DAY_END, 1380)
        val today = getTodayDateString()

        // Expired if the schedule belongs to a past day
        if (scheduleDate < today) {
            return true
        }

        // Expired if current time today has reached or passed dayEnd
        if (scheduleDate == today) {
            val cal = Calendar.getInstance()
            val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (currentMinute >= dayEnd) {
                return true
            }
        }

        return false
    }

    fun clearActiveSchedule() {
        prefs.edit()
            .remove(KEY_ACTIVE_SCHEDULE_JSON)
            .remove(KEY_SCHEDULE_DATE)
            .remove(KEY_DAY_START)
            .remove(KEY_DAY_END)
            .remove(KEY_SAVED_TIMESTAMP)
            .apply()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    companion object {
        private const val PREFS_NAME = "smart_scheduler_active_session"
        private const val KEY_ACTIVE_SCHEDULE_JSON = "active_schedule_json"
        private const val KEY_SCHEDULE_DATE = "active_schedule_date"
        private const val KEY_DAY_START = "active_day_start"
        private const val KEY_DAY_END = "active_day_end"
        private const val KEY_SAVED_TIMESTAMP = "active_saved_timestamp"
    }
}
