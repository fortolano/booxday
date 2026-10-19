package com.boox.day.editor

/** Contact lasts until the SDK delivers the completed stroke, regardless of its duration.
 * Ownership prevents a detached or late callback from another canvas releasing a live contact.
 */
internal class StylusContactGuard {
    @Volatile private var owner: String? = null

    val isActive: Boolean get() = owner != null

    @Synchronized fun begin(canvasOwner: String) {
        owner = canvasOwner
    }

    @Synchronized fun end(canvasOwner: String): Boolean {
        if (owner != canvasOwner) return false
        owner = null
        return true
    }
}
