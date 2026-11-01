package com.boox.day.sync.caldav

import at.bitfire.dav4jvm.BasicDigestAuthHandler
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.CalendarColor
import at.bitfire.dav4jvm.property.CalendarData
import at.bitfire.dav4jvm.property.CalendarHomeSet
import at.bitfire.dav4jvm.property.CurrentUserPrincipal
import at.bitfire.dav4jvm.property.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetCTag
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.ResourceType
import at.bitfire.dav4jvm.property.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.SyncToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * A CalDAV server as BooxDay talks to it (plan stage 3, step 3.3, Oct 2026): https only (owner
 * decision 5), Basic/Digest sign-in with the user's (app) password, finding the user's calendars
 * (RFC 6764 well-known, RFC 5397 principal, RFC 4791 calendar home). A server certificate Android
 * doesn't trust fails with [Failure.UntrustedCertificate], whose fingerprint the user may choose to
 * trust ("Trust This Server"); then [pinnedCertSha256] lets exactly that certificate through, for
 * this account only.
 */
internal class CalDavConnection(
    private val username: String,
    password: String,
    private val pinnedCertSha256: String? = null,
    baseClient: OkHttpClient = OkHttpClient()
) {
    /** Why a connection failed, in the words the sign-in screen needs. */
    sealed class Failure(message: String, cause: Throwable? = null) : IOException(message, cause) {
        class NotHttps : Failure("Not an https address")
        class BadAddress : Failure("Not a server address")
        class WrongPassword : Failure("The server refused the name or password")
        class NoCalendars : Failure("No calendars found at this address")
        class UntrustedCertificate(val sha256: String, val subject: String) : Failure("The server's certificate isn't trusted")
        class Offline(cause: Throwable) : Failure("No connection", cause)
        class Server(val code: Int) : Failure("The server answered $code")
    }

    data class Discovery(val principalUrl: String, val calendarHomeUrl: String, val calendars: List<RemoteCalendar>)

    /** One calendar collection. [href] is its URL path (BooxDay's calendarId). */
    data class RemoteCalendar(
        val href: String,
        val name: String,
        /** "#RRGGBB", or null when the server has none. */
        val colorHex: String?,
        val readOnly: Boolean,
        val ctag: String?,
        val syncToken: String?
    )

    /** The certificate of the last failed handshake, for [Failure.UntrustedCertificate]. */
    @Volatile
    private var rejected: X509Certificate? = null

    val client: OkHttpClient = baseClient.newBuilder()
        .followRedirects(false)  // DavResource follows them itself, https only
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .apply {
            // Both roles, as DAVx5 uses it: answers the first challenge, then signs every later request
            val auth = BasicDigestAuthHandler(domain = null, username = username, password = password)
            addNetworkInterceptor(auth)
            authenticator(auth)
        }
        .apply {
            val trust = PinningTrustManager(systemTrustManager(), pinnedCertSha256) { rejected = it }
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            sslSocketFactory(tls.socketFactory, trust)
            // The user trusted this exact certificate: its fingerprint is the proof, so a home server
            // reached by its IP address with a certificate made for its name works too
            val names = okhttp3.internal.tls.OkHostnameVerifier
            hostnameVerifier { host, session ->
                val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
                val ok = names.verify(host, session) ||
                    (pinnedCertSha256 != null && leaf != null && sha256(leaf).equals(pinnedCertSha256, ignoreCase = true))
                if (!ok && leaf != null) rejected = leaf
                ok
            }
        }
        .build()

    /** Finds the user's calendars from the address the user gave (or a ready-made one, owner decision 59). */
    suspend fun discover(address: String): Discovery = call {
        val base = httpsUrl(address)
        val principal = findPrincipal(base) ?: throw Failure.NoCalendars()
        val home = propfind(principal, 0, CalendarHomeSet.NAME).firstNotNullOfOrNull { response ->
            response[CalendarHomeSet::class.java]?.hrefs?.firstOrNull()?.let { response.href.resolve(it) }
        } ?: throw Failure.NoCalendars()
        val calendars = listCalendarsAt(home)
        if (calendars.isEmpty()) throw Failure.NoCalendars()
        Discovery(principal.toString(), home.toString(), calendars)
    }

    /** The calendars in the account's calendar home now (names, colours and change markers move). */
    suspend fun listCalendars(calendarHomeUrl: String): List<RemoteCalendar> = call { listCalendarsAt(httpsUrl(calendarHomeUrl)) }

    /** One event file as the server holds it. [href] is its URL path. */
    data class RemoteEventFile(val href: String, val etag: String?, val ics: String)

    /**
     * The calendar's event files with a moment in [start]..[end], path → change tag (RFC 4791
     * calendar-query with a time range: the server works out repeats). A file the server gives no tag
     * maps to null, so it is always downloaded. A calendar removed on the server fails with
     * [Failure.Server] 404 or 410.
     */
    suspend fun eventTags(calendarUrl: String, start: Date, end: Date): Map<String, String?> = call {
        val found = LinkedHashMap<String, String?>()
        DavCalendar(client, httpsUrl(calendarUrl)).calendarQuery("VEVENT", start, end) { response, relation ->
            if (relation != Response.HrefRelation.SELF && response.isSuccess()) {
                found[response.href.encodedPath] = response[GetETag::class.java]?.eTag
            }
        }
        found
    }

    /**
     * Saves an event file (step 3.5). [ifMatch] is the stored change tag: the server refuses with
     * [Failure.Server] 412 when the file changed there since (null: the server never gave one, no
     * guard). [create]: a new file, saved only if none is there yet (If-None-Match). Returns the new
     * change tag, or null when the server gives none (RFC 4791 5.3.4: then it changed the text, so
     * read it again).
     */
    suspend fun putEvent(fileUrl: String, ics: String, ifMatch: String?, create: Boolean): String? = call {
        var etag: String? = null
        DavResource(client, httpsUrl(fileUrl)).put(
            ics.toRequestBody(ICALENDAR), if (create) null else ifMatch, null, create
        ) { response -> etag = GetETag.fromResponse(response)?.eTag }
        etag
    }

    /** Deletes an event file if it is unchanged ([ifMatch], 412 otherwise); one already gone counts as deleted. */
    suspend fun deleteEvent(fileUrl: String, ifMatch: String?) = call {
        try {
            DavResource(client, httpsUrl(fileUrl)).delete(ifMatch, null) { }
        } catch (e: HttpException) {
            if (e.code != 404 && e.code != 410) throw e
        }
    }

    /** The files at [hrefs] (paths in the calendar), [MULTIGET_BATCH] per request; files gone meanwhile are left out. */
    suspend fun downloadEvents(calendarUrl: String, hrefs: List<String>): List<RemoteEventFile> = call {
        val url = httpsUrl(calendarUrl)
        val files = mutableListOf<RemoteEventFile>()
        for (batch in hrefs.chunked(MULTIGET_BATCH)) {
            DavCalendar(client, url).multiget(batch.mapNotNull { url.resolve(it) }) { response, _ ->
                val ics = response[CalendarData::class.java]?.iCalendar
                if (response.isSuccess() && ics != null) {
                    files += RemoteEventFile(response.href.encodedPath, response[GetETag::class.java]?.eTag, ics)
                }
            }
        }
        files
    }

    // ── Discovery steps ──

    /** RFC 6764: the well-known address first, then the address itself; null when neither names a principal. */
    private fun findPrincipal(base: HttpUrl): HttpUrl? {
        val wellKnown = base.resolve("/.well-known/caldav")
        for (start in listOfNotNull(wellKnown, base).distinct()) {
            val found = try {
                propfind(start, 0, CurrentUserPrincipal.NAME).firstNotNullOfOrNull { response ->
                    response[CurrentUserPrincipal::class.java]?.href?.let { response.href.resolve(it) }
                }
            } catch (e: HttpException) {
                if (e.code == 401) throw Failure.WrongPassword()
                null  // 404, 405 and the like: try the next address
            }
            if (found != null) return found
        }
        return null
    }

    private fun listCalendarsAt(home: HttpUrl): List<RemoteCalendar> =
        propfind(
            home, 1,
            ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
            CurrentUserPrivilegeSet.NAME, GetCTag.NAME, SyncToken.NAME
        ).mapNotNull { response ->
            val types = response[ResourceType::class.java]?.types.orEmpty()
            if (ResourceType.CALENDAR !in types) return@mapNotNull null
            // No component set means every kind is allowed (RFC 4791 5.2.3)
            val events = response[SupportedCalendarComponentSet::class.java]?.supportsEvents ?: true
            if (!events) return@mapNotNull null
            val privileges = response[CurrentUserPrivilegeSet::class.java]
            RemoteCalendar(
                href = response.href.encodedPath,
                name = response[DisplayName::class.java]?.displayName?.takeIf { it.isNotBlank() }
                    ?: response.href.pathSegments.lastOrNull { it.isNotBlank() } ?: "Calendar",
                colorHex = response[CalendarColor::class.java]?.color?.let { "#%06X".format(it and 0xFFFFFF) },
                readOnly = privileges != null && !privileges.mayWriteContent,
                ctag = response[GetCTag::class.java]?.cTag,
                syncToken = response[SyncToken::class.java]?.token
            )
        }

    private fun propfind(url: HttpUrl, depth: Int, vararg properties: Property.Name): List<Response> {
        val found = mutableListOf<Response>()
        DavResource(client, url).propfind(depth, *properties) { response, _ -> found += response }
        return found
    }

    // ── Plumbing ──

    private fun httpsUrl(address: String): HttpUrl {
        val trimmed = address.trim()
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: throw Failure.BadAddress()
        if (!url.isHttps) throw Failure.NotHttps()
        return url
    }

    /** Off the caller's thread; failures as [Failure]. */
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Failure) {
            throw e
        } catch (e: HttpException) {
            throw if (e.code == 401) Failure.WrongPassword() else Failure.Server(e.code)
        } catch (e: SSLHandshakeException) {
            val cert = rejected ?: throw Failure.Offline(e)
            throw Failure.UntrustedCertificate(sha256(cert), cert.subjectX500Principal.name)
        } catch (e: SSLPeerUnverifiedException) {
            // A certificate for another name: offered like an untrusted one
            val cert = rejected ?: throw Failure.Offline(e)
            throw Failure.UntrustedCertificate(sha256(cert), cert.subjectX500Principal.name)
        } catch (e: IOException) {
            throw Failure.Offline(e)
        }
    }

    companion object {
        /** Files per multiget request: small enough for any server's request limits. */
        const val MULTIGET_BATCH = 50

        private val ICALENDAR = "text/calendar; charset=utf-8".toMediaType()

        /** "AB:CD:…", as browsers show a certificate's SHA-256 fingerprint. */
        fun sha256(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

        private fun systemTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }

    /**
     * Android's own trust, plus the one certificate the user chose to trust for this account. Every
     * other certificate fails as usual; the rejected one is handed to [onRejected] so its fingerprint
     * can be shown.
     */
    private class PinningTrustManager(
        private val system: X509TrustManager,
        private val pinnedSha256: String?,
        private val onRejected: (X509Certificate) -> Unit
    ) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (pinnedSha256 != null && chain.isNotEmpty() && sha256(chain[0]).equals(pinnedSha256, ignoreCase = true)) return
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                chain.firstOrNull()?.let(onRejected)
                throw e
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }
}
