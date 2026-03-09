package com.guardian.childmobile

import android.app.usage.UsageStatsManager
import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RNUsageModule(private val appContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(appContext) {

    private val usageStatsManager: UsageStatsManager? =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    override fun getName(): String = "RNUsageModule"

    @ReactMethod
    fun getUsageOverview(startTime: Double, endTime: Double, promise: Promise) {
        try {
            val manager = usageStatsManager
            if (manager == null) {
                promise.reject(
                    "USAGE_STATS_UNAVAILABLE",
                    "UsageStatsManager is not available on this device",
                )
                return
            }

            val start = startTime.toLong()
            val end = endTime.toLong()

            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                start,
                end,
            )

            if (stats.isNullOrEmpty()) {
                // No native data for this range; let JS fall back to API.
                promise.reject(
                    "NO_USAGE_DATA",
                    "No usage stats available for the requested range",
                    null,
                )
                return
            }

            val result = Arguments.createArray()

            for (stat in stats) {
                if (stat.totalTimeInForeground <= 0L) continue

                val map = Arguments.createMap().apply {
                    // Map to NativeUsageSession shape expected by the JS bridge.
                    putString("id", "${stat.packageName}:${stat.lastTimeUsed}")
                    putString("deviceId", "local-device")
                    putString("childId", "local-child")
                    putString("itemName", stat.packageName)
                    putDouble("durationSeconds", stat.totalTimeInForeground / 1000.0)
                    putString("timestamp", toIso8601(stat.lastTimeUsed))
                }

                result.pushMap(map)
            }

            promise.resolve(result)
        } catch (e: SecurityException) {
            // Missing PACKAGE_USAGE_STATS permission or similar; allow JS to fall back.
            promise.reject("USAGE_STATS_PERMISSION_DENIED", e.message, e)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message, e)
        }
    }

    private fun toIso8601(ms: Long): String {
        val date = Date(ms)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
