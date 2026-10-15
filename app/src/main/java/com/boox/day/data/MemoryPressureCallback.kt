package com.boox.day.data

/**
 * Callback interface for receiving memory pressure events from the system.
 * Registered callbacks are notified when Android's ComponentCallbacks2.onTrimMemory() fires.
 */
fun interface MemoryPressureCallback {
    /**
     * Called when the system signals memory pressure.
     * @param level The trim memory level from ComponentCallbacks2 (e.g., TRIM_MEMORY_RUNNING_LOW)
     */
    fun onMemoryPressure(level: Int)
}
