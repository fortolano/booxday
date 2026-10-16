package com.boox.day.api

import com.boox.day.ProjectInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** Connect OpenRouter (OAuth PKCE): the parts that need no network or device. */
class OpenRouterConnectTest {

    @Test
    fun `S256 challenge matches the RFC 7636 example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            OpenRouterConnect.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    @Test
    fun `secrets are 43 url-safe characters and different each time`() {
        val a = OpenRouterConnect.newSecret()
        val b = OpenRouterConnect.newSecret()
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, b)
    }

    @Test
    fun `sign-in address carries the return page, challenge, label and state`() {
        val url = OpenRouterConnect.authUrl(ProjectInfo.CONNECT_PAGE_URL, "chal-lenge", "st_ate")
        assertTrue(url.startsWith("https://openrouter.ai/auth?"))
        val params = url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals("https://fortolano.github.io/booxday/openrouter/", params["callback_url"])
        assertEquals("chal-lenge", params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals("Boox Day", params["key_label"])
        assertEquals("st_ate", params["state"])
    }

    @Test
    fun `reads code and state from the return page's link`() {
        // The page builds it with encodeURIComponent
        val params = OpenRouterConnect.parseLink("booxday-connect://openrouter?code=abc%2B123%26x%3D1&state=st%20ate")!!
        assertEquals("abc+123&x=1", params["code"])
        assertEquals("st ate", params["state"])
        assertNull(OpenRouterConnect.parseLink("booxday://page-123"))
        assertNull(OpenRouterConnect.parseLink(null))
        assertEquals(emptyMap<String, String>(), OpenRouterConnect.parseLink("booxday-connect://openrouter"))
    }

    @Test
    fun `a code is only used with the connection started here, within 10 minutes`() {
        val now = 1_000_000_000L
        val pending = OpenRouterConnect.Pending("verifier", "state-1", now - 60_000)
        assertNull(OpenRouterConnect.problemWith(pending, "code", "state-1", now, checkState = true))
        assertNotNull(OpenRouterConnect.problemWith(pending, "code", "other", now, checkState = true))
        assertNotNull(OpenRouterConnect.problemWith(pending, "code", null, now, checkState = true))
        assertNotNull(OpenRouterConnect.problemWith(null, "code", "state-1", now, checkState = true))
        assertNotNull(OpenRouterConnect.problemWith(pending, "", "state-1", now, checkState = true))
        val old = pending.copy(createdAtMs = now - OpenRouterConnect.CODE_LIFETIME_MS - 1)
        assertNotNull(OpenRouterConnect.problemWith(old, "code", "state-1", now, checkState = true))
        // A pasted code has no state: the verifier alone protects it
        assertNull(OpenRouterConnect.problemWith(pending, "code", null, now, checkState = false))
    }

    @Test
    fun `exchange request and answer`() {
        val body = Json.parseToJsonElement(OpenRouterConnect.exchangeBody("the-code", "the-verifier")).jsonObject
        assertEquals("the-code", body["code"]!!.jsonPrimitive.content)
        assertEquals("the-verifier", body["code_verifier"]!!.jsonPrimitive.content)
        assertEquals("S256", body["code_challenge_method"]!!.jsonPrimitive.content)

        assertEquals("sk-or-v1-abc", OpenRouterConnect.keyFromExchange("""{"key":"sk-or-v1-abc","user_id":"user_1"}"""))
        assertNull(OpenRouterConnect.keyFromExchange("""{"user_id":"user_1"}"""))
        assertNull(OpenRouterConnect.keyFromExchange("""{"key":""}"""))
        assertNull(OpenRouterConnect.keyFromExchange("not json"))
    }

    @Test
    fun `tells a key from a connection code`() {
        assertTrue(OpenRouterConnect.looksLikeKey("  sk-or-v1-0123  "))
        assertFalse(OpenRouterConnect.looksLikeKey("d7f3c1a9-5b2e-4c8f-9a61-3e0b7d24f915"))
    }
}
