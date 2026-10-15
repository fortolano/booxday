package com.boox.day

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * TEMPORARY (Oct 2026, MainScreen JIT measurement), debug builds only: for each finger lift, logs
 * the time from the lift to the end of the first frame drawn after the click handler ran, with
 * that frame's stages (FrameMetrics). Remove after measuring.
 */
object PerfProbe {
    private const val TAG = "PerfProbe"
    private const val WINDOW_NS = 1_500_000_000L

    private val lock = Any()
    private var upNs = 0L          // finger lift (event time)
    private var handlerEndNs = 0L  // click handler finished
    private var handlerNs = 0L
    private var firstLogged = true
    private var frames = 0
    private var lastEndNs = 0L
    private var busyNs = 0L
    private var seq = 0

    fun install(app: Application) {
        if (!BuildConfig.DEBUG) return
        val thread = HandlerThread("PerfProbe").apply { start() }
        val handler = Handler(thread.looper)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) = hook(a, handler)
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    private fun hook(activity: Activity, handler: Handler) {
        val window = activity.window
        val original = window.callback ?: return
        window.callback = Proxy.newProxyInstance(
            Window.Callback::class.java.classLoader,
            arrayOf(Window.Callback::class.java)
        ) { _, method, args ->
            val event = if (method.name == "dispatchTouchEvent") args?.get(0) as? MotionEvent else null
            val isLift = event != null && event.actionMasked == MotionEvent.ACTION_UP &&
                event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
            val start = System.nanoTime()
            val result = try {
                method.invoke(original, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
            if (isLift) {
                val end = System.nanoTime()
                mark(event!!.eventTime, end, end - start, handler)
                // A tap may open a popup (its own window): watch its touches too
                for (delay in longArrayOf(300, 800, 1500)) activity.window.decorView.postDelayed({ hookPopups(handler) }, delay)
            }
            result
        } as Window.Callback

        window.addOnFrameMetricsAvailableListener({ _, m, _ ->
            val vsync = m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            val frameStart = vsync + m.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
            val end = vsync + m.getMetric(FrameMetrics.TOTAL_DURATION)
            synchronized(lock) {
                if (upNs == 0L || frameStart < handlerEndNs || end - upNs > WINDOW_NS) return@synchronized
                frames++
                lastEndNs = end
                busyNs += m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
                    m.getMetric(FrameMetrics.ANIMATION_DURATION) +
                    m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                    m.getMetric(FrameMetrics.DRAW_DURATION)
                if (!firstLogged) {
                    firstLogged = true
                    Log.i(
                        TAG, "tap#$seq first ${ms(end - upNs)} ms (handler ${ms(handlerNs)}, wait ${ms(frameStart - handlerEndNs)}," +
                            " anim ${ms(m.getMetric(FrameMetrics.ANIMATION_DURATION))}," +
                            " layout ${ms(m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))}," +
                            " draw ${ms(m.getMetric(FrameMetrics.DRAW_DURATION))}," +
                            " sync+gpu ${ms(m.getMetric(FrameMetrics.SYNC_DURATION) + m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) + m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))})"
                    )
                }
            }
        }, handler)
    }

    private fun mark(eventTimeMs: Long, endNs: Long, handlerDurationNs: Long, handler: Handler) {
        val tap: Int
        synchronized(lock) {
            upNs = eventTimeMs * 1_000_000L
            handlerEndNs = endNs
            handlerNs = handlerDurationNs
            firstLogged = false
            frames = 0
            lastEndNs = 0L
            busyNs = 0L
            tap = ++seq
        }
        handler.postDelayed({ summary(tap) }, WINDOW_NS / 1_000_000L)
    }

    private val hookedPopups = java.util.WeakHashMap<android.view.View, Boolean>()

    /** Popups dismiss on a touch outside them (ACTION_OUTSIDE / ACTION_DOWN): mark that touch. */
    private fun hookPopups(handler: Handler) {
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val instance = wmg.getMethod("getInstance").invoke(null)
            val field = wmg.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val views = ArrayList(field.get(instance) as List<android.view.View>)
            for (v in views) {
                if (hookedPopups.containsKey(v) || v.javaClass.name.contains("DecorView")) continue
                hookedPopups[v] = true
                v.setOnTouchListener { _, e ->
                    if (e.actionMasked == MotionEvent.ACTION_OUTSIDE || e.actionMasked == MotionEvent.ACTION_UP) {
                        mark(e.eventTime, System.nanoTime(), 0L, handler)
                    }
                    false
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "popup hook failed: $t")
        }
    }

    private fun summary(tap: Int) {
        synchronized(lock) {
            if (tap != seq) return
            Log.i(TAG, "tap#$tap settled ${if (lastEndNs > 0) ms(lastEndNs - upNs) else "-"} ms, frames $frames, busy ${ms(busyNs)} ms")
        }
    }

    private fun ms(ns: Long) = String.format(java.util.Locale.US, "%.1f", ns / 1_000_000.0)
}
