package com.boox.day.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** One request must never replace the one before it: Save as Note and a task reload come back to back. */
class MainScreenRequestsTest {
    @Test
    fun `requests made together are all handled, in order`() = runBlocking {
        MainScreenRequests.request(MainScreenRequest.ReloadMemoPages)
        MainScreenRequests.request(MainScreenRequest.ReloadTasks)

        assertEquals(MainScreenRequest.ReloadMemoPages, MainScreenRequests.next())
        assertEquals(MainScreenRequest.ReloadTasks, MainScreenRequests.next())
    }
}
