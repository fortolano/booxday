package com.boox.day.sync.caldav

import at.bitfire.dav4jvm.BasicDigestAuthHandler
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.XmlUtils
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

    /** What the account's server has: its calendars and its task lists (owner decision 78: either may be empty, not both). */
    data class Discovery(
        val principalUrl: String,
        val calendarHomeUrl: String,
        val calendars: List<RemoteCalendar>,
        val taskLists: List<RemoteCalendar> = emptyList()
    )

    /** One calendar collection. [href] is its URL path (BooxDay's calendarId). */
    data class RemoteCalendar(
        val href: String,
        val name: String,
        /** "#RRGGBB", or null when the server has none. */
        val colorHex: String?,
        val readOnly: Boolean,
        val ctag: String?,
        val syncToken: String?,
        /** Shared with others or by someone (CalendarServer's shared / shared-owner): an iCloud Family calendar. */
        val shared: Boolean = false
    )

    /** The certificate of the last failed handshake, for [Failure.UntrustedCertificate]. */
    @Volatile
    private var rejected: X509Certificate? = null

    /** Hostnames explicitly requested via [httpsUrl] so custom certificate pinning stays scoped to approved endpoints. */
    private val approvedHosts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
            // The user trusted this exact certificate: its fingerprint is the proof, scoped to approved server hosts
            val names = okhttp3.internal.tls.OkHostnameVerifier
            hostnameVerifier { host, session ->
                val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
                val hostApproved = synchronized(approvedHosts) {
                    approvedHosts.isEmpty() || approvedHosts.any { it.equals(host, ignoreCase = true) }
                }
                val ok = names.verify(host, session) ||
                    (hostApproved && pinnedCertSha256 != null && leaf != null && sha256(leaf).equals(pinnedCertSha256, ignoreCase = true))
                if (!ok && leaf != null) rejected = leaf
                ok
            }
        }
        .build()

    /** Finds the user's calendars and task lists from the address the user gave (or a ready-made one, owner decision 59). */
    suspend fun discover(address: String): Discovery = call {
        val base = httpsUrl(address)
        val principal = findPrincipal(base) ?: throw Failure.NoCalendars()
        val home = propfind(principal, 0, CalendarHomeSet.NAME).firstNotNullOfOrNull { response ->
            response[CalendarHomeSet::class.java]?.hrefs?.firstOrNull()?.let { requireHttpsDerived(response.href.resolve(it)) }
        } ?: throw Failure.NoCalendars()
        val calendars = listCalendarsAt(home)
        val taskLists = listCalendarsAt(home, tasks = true)
        // A server with task lists and no calendar is used for tasks (owner decision 78); with neither it isn't one
        if (calendars.isEmpty() && taskLists.isEmpty()) throw Failure.NoCalendars()
        Discovery(principal.toString(), home.toString(), calendars, taskLists)
    }

    /**
     * The calendar the server uses for new events (RFC 6638 schedule-default-calendar-URL, on the
     * user's scheduling inbox), as a path; null when the server names none or can't say. The inbox is
     * the one the principal names, else the inbox collection in the calendar home (iCloud names none).
     */
    suspend fun defaultCalendarHref(principalUrl: String, calendarHomeUrl: String): String? = call {
        CalDavSchedulingProperties.register()
        try {
            val inbox = propfind(httpsUrl(principalUrl), 0, ScheduleInboxUrl.NAME).firstNotNullOfOrNull { response ->
                response[ScheduleInboxUrl::class.java]?.href?.let { requireHttpsDerived(response.href.resolve(it)) }
            } ?: propfind(httpsUrl(calendarHomeUrl), 1, ResourceType.NAME).firstOrNull { response ->
                response[ResourceType::class.java]?.types.orEmpty().any { it.namespace == XmlUtils.NS_CALDAV && it.name == "schedule-inbox" }
            }?.href
            inbox?.let {
                propfind(it, 0, ScheduleDefaultCalendarUrl.NAME).firstNotNullOfOrNull { response ->
                    response[ScheduleDefaultCalendarUrl::class.java]?.url?.let { url -> response.href.resolve(url)?.encodedPath }
                }
            }
        } catch (e: HttpException) {
            null  // no scheduling on this server: the first calendar that isn't shared
        }
    }

    /** The calendars in the account's calendar home now (names, colours and change markers move). */
    suspend fun listCalendars(calendarHomeUrl: String): List<RemoteCalendar> = call { listCalendarsAt(httpsUrl(calendarHomeUrl)) }

    /**
     * The task lists in the account's calendar home (plan stage 4, step 4.1): collections that take
     * tasks (VTODO). One that takes events too (Nextcloud's "Personal") is a calendar and a task list.
     */
    suspend fun listTaskLists(calendarHomeUrl: String): List<RemoteCalendar> =
        call { listCalendarsAt(httpsUrl(calendarHomeUrl), tasks = true) }

    /**
     * A collection's change marker (getctag, else the RFC 6578 sync-token): the same marker means
     * nothing in it changed. Null when the server has neither. A collection removed on the server
     * fails with [Failure.Server] 404 or 410.
     */
    suspend fun collectionTag(collectionUrl: String): String? = call {
        propfind(httpsUrl(collectionUrl), 0, GetCTag.NAME, SyncToken.NAME).firstNotNullOfOrNull { response ->
            response[GetCTag::class.java]?.cTag ?: response[SyncToken::class.java]?.token
        }
    }

    /**
     * Result of a CalDAV member tag listing (RFC 4791 calendar-query).
     * [complete] is false when any member (or SELF) fails with a non-2xx status other than 404/410,
     * so callers never infer deletions or advance sync cursors from an incomplete listing.
     */
    data class TagListing(val tags: Map<String, String?>, val complete: Boolean = true)

    /**
     * Result of a CalDAV multiget download.
     * [gone] holds hrefs authoritatively reported as 404/410; [complete] is false when any
     * requested href failed with another error or was omitted without 404/410.
     */
    data class DownloadBatch(
        val files: List<RemoteEventFile>,
        val gone: Set<String> = emptySet(),
        val complete: Boolean = true,
    )

    private fun isAuthoritativeMissing(response: Response): Boolean {
        val code = response.status?.code
        if (code == 404 || code == 410) return true
        return code == null &&
            response.propstat.isNotEmpty() &&
            response.propstat.all { it.status.code == 404 || it.status.code == 410 }
    }

    /** A task list's task files, path → change tag (RFC 4791 calendar-query for VTODO, no time range). */
    suspend fun taskTagListing(listUrl: String): TagListing = call {
        val found = LinkedHashMap<String, String?>()
        var complete = true
        DavCalendar(client, httpsUrl(listUrl)).calendarQuery("VTODO", null, null) { response, relation ->
            if (relation == Response.HrefRelation.SELF) {
                if (!response.isSuccess() && !isAuthoritativeMissing(response)) complete = false
                return@calendarQuery
            }
            if (response.isSuccess()) {
                found[response.href.encodedPath] = response[GetETag::class.java]?.eTag
            } else if (!isAuthoritativeMissing(response)) {
                complete = false
            }
        }
        TagListing(found, complete)
    }

    /** A task list's task files, path → change tag (RFC 4791 calendar-query for VTODO, no time range). */
    suspend fun taskTags(listUrl: String): Map<String, String?> = taskTagListing(listUrl).tags

    /**
     * Moves a file to another collection (WebDAV MOVE, never over an existing file). Servers that
     * don't move between collections answer [Failure.Server] 403, 405, 501 or 502: then copy and delete.
     */
    suspend fun moveFile(fromUrl: String, toUrl: String) = call {
        DavResource(client, httpsUrl(fromUrl)).move(httpsUrl(toUrl), false) { }
    }

    /** One event (or task) file as the server holds it. [href] is its URL path. */
    data class RemoteEventFile(val href: String, val etag: String?, val ics: String)

    /**
     * The calendar's event files with a moment in [start]..[end], path → change tag (RFC 4791
     * calendar-query with a time range: the server works out repeats). A file the server gives no tag
     * maps to null, so it is always downloaded. A calendar removed on the server fails with
     * [Failure.Server] 404 or 410.
     */
    suspend fun eventTagListing(calendarUrl: String, start: Date, end: Date): TagListing = call {
        val found = LinkedHashMap<String, String?>()
        var complete = true
        DavCalendar(client, httpsUrl(calendarUrl)).calendarQuery("VEVENT", start, end) { response, relation ->
            if (relation == Response.HrefRelation.SELF) {
                if (!response.isSuccess() && !isAuthoritativeMissing(response)) complete = false
                return@calendarQuery
            }
            if (response.isSuccess()) {
                found[response.href.encodedPath] = response[GetETag::class.java]?.eTag
            } else if (!isAuthoritativeMissing(response)) {
                complete = false
            }
        }
        TagListing(found, complete)
    }

    suspend fun eventTags(calendarUrl: String, start: Date, end: Date): Map<String, String?> =
        eventTagListing(calendarUrl, start, end).tags

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

    /** The files at [hrefs] (paths in the calendar), [MULTIGET_BATCH] per request, distinguishing 404/410 from failed/missing entries. */
    suspend fun downloadEventsResult(calendarUrl: String, hrefs: List<String>): DownloadBatch = call {
        val url = httpsUrl(calendarUrl)
        val files = mutableListOf<RemoteEventFile>()
        val gone = mutableSetOf<String>()
        var complete = true
        for (batch in hrefs.chunked(MULTIGET_BATCH)) {
            val resolved = batch.mapNotNull { href -> url.resolve(href)?.takeIf { it.isHttps }?.let { href to it } }
            if (resolved.size != batch.size) complete = false
            val expectedPaths = resolved.map { it.second.encodedPath }.toSet()
            val chunkAccounted = mutableSetOf<String>()
            DavCalendar(client, url).multiget(resolved.map { it.second }) { response, _ ->
                val path = response.href.encodedPath
                if (path !in expectedPaths) return@multiget
                val ics = response[CalendarData::class.java]?.iCalendar
                when {
                    response.isSuccess() && ics != null -> {
                        files += RemoteEventFile(path, response[GetETag::class.java]?.eTag, ics)
                        chunkAccounted += path
                    }
                    isAuthoritativeMissing(response) -> {
                        gone += path
                        chunkAccounted += path
                    }
                    else -> {
                        complete = false
                    }
                }
            }
            if (!expectedPaths.all { it in chunkAccounted }) complete = false
        }
        DownloadBatch(files = files, gone = gone, complete = complete)
    }

    /** The files at [hrefs] (paths in the calendar), [MULTIGET_BATCH] per request; files gone meanwhile are left out. */
    suspend fun downloadEvents(calendarUrl: String, hrefs: List<String>): List<RemoteEventFile> =
        downloadEventsResult(calendarUrl, hrefs).files

    // ── Discovery steps ──

    /** RFC 6764: the well-known address first, then the address itself; null when neither names a principal. */
    private fun findPrincipal(base: HttpUrl): HttpUrl? {
        val wellKnown = base.resolve("/.well-known/caldav")
        for (start in listOfNotNull(wellKnown, base).distinct()) {
            val found = try {
                propfind(start, 0, CurrentUserPrincipal.NAME).firstNotNullOfOrNull { response ->
                    response[CurrentUserPrincipal::class.java]?.href?.let { requireHttpsDerived(response.href.resolve(it)) }
                }
            } catch (e: HttpException) {
                if (e.code == 401) throw Failure.WrongPassword()
                null  // 404, 405 and the like: try the next address
            }
            if (found != null) return found
        }
        return null
    }

    /** The collections in [home] that take events, or with [tasks] the ones that take tasks. */
    private fun listCalendarsAt(home: HttpUrl, tasks: Boolean = false): List<RemoteCalendar> =
        propfind(
            home, 1,
            ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
            CurrentUserPrivilegeSet.NAME, GetCTag.NAME, SyncToken.NAME
        ).mapNotNull { response ->
            val types = response[ResourceType::class.java]?.types.orEmpty()
            if (ResourceType.CALENDAR !in types) return@mapNotNull null
            // No component set means every kind is allowed (RFC 4791 5.2.3)
            val kinds = response[SupportedCalendarComponentSet::class.java]
            val takes = if (tasks) kinds?.supportsTasks ?: true else kinds?.supportsEvents ?: true
            if (!takes) return@mapNotNull null
            val privileges = response[CurrentUserPrivilegeSet::class.java]
            RemoteCalendar(
                href = response.href.encodedPath,
                name = response[DisplayName::class.java]?.displayName?.takeIf { it.isNotBlank() }
                    ?: response.href.pathSegments.lastOrNull { it.isNotBlank() } ?: "Calendar",
                colorHex = response[CalendarColor::class.java]?.color?.let { "#%06X".format(it and 0xFFFFFF) },
                readOnly = privileges != null && !privileges.mayWriteContent,
                ctag = response[GetCTag::class.java]?.cTag,
                syncToken = response[SyncToken::class.java]?.token,
                shared = types.any { it.namespace == XmlUtils.NS_CALENDARSERVER && (it.name == "shared" || it.name == "shared-owner") }
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
        approvedHosts.add(url.host)
        return url
    }

    /** Ensures a server-returned discovery URL stays HTTPS and registers its host once validated. */
    private fun requireHttpsDerived(resolved: HttpUrl?): HttpUrl? {
        val url = resolved ?: return null
        if (!url.isHttps) throw Failure.NotHttps()
        approvedHosts.add(url.host)
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
