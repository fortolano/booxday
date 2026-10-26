package com.boox.day.compat

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.rx.RxManager
import com.onyx.android.sdk.utils.DeviceFeatureUtil

object OnyxCompat {
    private const val TAG = "OnyxCompat"

    const val DEFAULT_SCREEN_WIDTH = 1872
    const val DEFAULT_SCREEN_HEIGHT = 1404
    const val DEFAULT_MAX_PRESSURE = 4095f

    fun initRxManager(context: Context) {
        RxManager.Builder.initAppContext(context)
    }

    fun getScreenWidthOrDefault(defaultValue: Int = DEFAULT_SCREEN_WIDTH): Int {
        return try {
            EpdController.getEpdHeight().toInt()
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back to default screen width", t)
            defaultValue
        }
    }

    fun getScreenHeightOrDefault(defaultValue: Int = DEFAULT_SCREEN_HEIGHT): Int {
        return try {
            EpdController.getEpdWidth().toInt()
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back to default screen height", t)
            defaultValue
        }
    }

    fun getMaxTouchPressureOrDefault(defaultValue: Float = DEFAULT_MAX_PRESSURE): Float {
        return try {
            val reported = EpdController.getMaxTouchPressure()
            val resolved = TouchPressureRange.resolve(reported, defaultValue)
            if (reported != resolved) Log.w(TAG, "Invalid vendor pressure range; using fallback")
            resolved
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back to default touch pressure", t)
            TouchPressureRange.resolve(Float.NaN, defaultValue)
        }
    }

    fun usesAndroidPenInput(context: Context): Boolean = !DeviceFeatureUtil.hasStylus(context)

    /**
     * The pen backend chosen at the last [createTouchHelper], for the crash report (Oct 2026): it is
     * logged only once at start, so it was usually gone from the report's last log lines.
     */
    @Volatile
    var penBackend: String = "not started"
        private set

    fun createTouchHelper(view: SurfaceView, callback: RawInputCallback, androidPen: Boolean): TouchHelper {
        // The SDK detector identifies its direct raw-reader devices, not all pen hardware.
        // Unrecognized/active pens use actual Android stylus events via APP_PEN, never finger input.
        val feature = if (androidPen) TouchHelper.FEATURE_APP_PEN_TOUCH_RENDER
            else TouchHelper.FEATURE_SF_TOUCH_RENDER
        penBackend = if (androidPen) "Android pen / EPD" else "SurfaceFlinger"
        Log.i(TAG, "Creating BOOX pen backend: $penBackend")
        return TouchHelper.create(view, feature, callback)
    }
}
