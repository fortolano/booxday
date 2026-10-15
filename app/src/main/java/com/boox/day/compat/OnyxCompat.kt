package com.boox.day.compat

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.rx.RxManager

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
            EpdController.getMaxTouchPressure()
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back to default touch pressure", t)
            defaultValue
        }
    }

    fun createTouchHelper(view: SurfaceView, callback: RawInputCallback): TouchHelper {
        return TouchHelper.create(view, callback)
    }
}
