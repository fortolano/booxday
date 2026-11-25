package com.boox.day.sync.microsoft

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The Graph failures To Do and OneDrive backup act on: a refused token is refreshed once before
 * "sign in again" (a wrong Reconnect would stop sync), throttling waits Microsoft's time, and other
 * refusals keep Graph's code.
 */
class MicrosoftGraphTest {
    private val server = MockWebServer()
    private val forced = mutableListOf<Boolean>()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun graph() = MicrosoftGraph(
        token = { force -> forced += force; if (force) "fresh" else "cached" },
        baseUrl = server.url("/v1.0").toString().trimEnd('/')
    )

    @Test
    fun refusedTokenIsRefreshedOnceThenWorks() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"value":[]}"""))

        val answer = graph().call("GET", "/me/todo/lists")

        assertTrue(answer!!.containsKey("value"))
        assertEquals(listOf(false, true), forced)
        assertEquals("Bearer cached", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun refusedTwiceMeansSignInAgain() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            graph().call("GET", "/me/todo/lists")
            fail("expected SignInNeeded")
        } catch (e: GraphException.SignInNeeded) {
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun throttlingWaitsMicrosoftsTime() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7"))
        try {
            graph().call("GET", "/me/todo/lists")
            fail("expected Busy")
        } catch (e: GraphException.Busy) {
            assertEquals(7_000L, e.retryAfterMs)
        }
    }

    @Test
    fun otherRefusalsKeepGraphsCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":"ErrorItemNotFound","message":"x"}}"""))
        try {
            graph().call("DELETE", "/me/todo/lists/a/tasks/b")
            fail("expected Http")
        } catch (e: GraphException.Http) {
            assertEquals(404, e.status)
            assertEquals("ErrorItemNotFound", e.code)
        }
    }

    @Test
    fun noContentIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(null, graph().call("DELETE", "/me/todo/lists/a/tasks/b"))
    }
}
