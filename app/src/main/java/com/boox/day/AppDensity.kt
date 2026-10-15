package com.boox.day

import android.content.res.Resources

/**
 * The app's fixed density (Oct 2026): the single source of truth for dp sizes on every tablet.
 *
 * - 10.3-inch and larger screens (short side at least 1600 px, Go 10.3 1860 x 2480 px): 385 dpi,
 *   773 x 1031 dp.
 * - Smaller screens (Go 6 1072 x 1448 px): 300 dpi, 572 x 772 dp.
 *
 * Independent of the BOOX per-app DPI setting (`dumpsys oec_service` → dpiConfig). BOOX firmware
 * rewrites the density inside `ResourcesImpl.updateConfigurationImpl` on every resources update in
 * this process, so it replaces whatever MainActivity set in attachBaseContext and onResume. Compose
 * reads the density from these resources when the screen and each popup window are created, so
 * MainActivity and BooxDayApp put this value back in getResources() through [apply].
 */
object AppDensity {
    private const val LARGE_SCREEN_DPI = 385
    private const val SMALL_SCREEN_DPI = 300
    private const val LARGE_SCREEN_MIN_SHORT_SIDE_PX = 1600

    /** The app's DPI on this tablet; fixed for the life of the process. */
    val dpi: Int by lazy {
        // System metrics are the physical display; the short side is the portrait width even if
        // the tablet was held in landscape at launch
        val metrics = Resources.getSystem().displayMetrics
        val shortSidePx = minOf(metrics.widthPixels, metrics.heightPixels)
        if (shortSidePx >= LARGE_SCREEN_MIN_SHORT_SIDE_PX) LARGE_SCREEN_DPI else SMALL_SCREEN_DPI
    }

    /**
     * Puts the app's density on [resources] if something (the BOOX hook) changed it. Field writes
     * only, never updateConfiguration, so it doesn't run the BOOX hook again or cause a relayout.
     */
    fun apply(resources: Resources, targetDpi: Int = dpi) {
        val metrics = resources.displayMetrics
        val config = resources.configuration
        val density = targetDpi / 160f
        val widthDp = (metrics.widthPixels / density).toInt()
        val heightDp = (metrics.heightPixels / density).toInt()
        if (metrics.densityDpi == targetDpi && config.densityDpi == targetDpi &&
            config.screenWidthDp == widthDp && config.screenHeightDp == heightDp
        ) return

        metrics.densityDpi = targetDpi
        metrics.density = density
        @Suppress("DEPRECATION")
        metrics.scaledDensity = density * (if (config.fontScale > 0f) config.fontScale else 1f)
        metrics.xdpi = targetDpi.toFloat()
        metrics.ydpi = targetDpi.toFloat()
        config.densityDpi = targetDpi
        config.screenWidthDp = widthDp
        config.screenHeightDp = heightDp
        config.smallestScreenWidthDp = minOf(widthDp, heightDp)
    }
}
