# ProGuard rules for BooxDay
# Generated for R8 optimization (Phase 4 APK size reduction)

#==============================================================================
# DEBUGGING - Keep line numbers for crash reports
#==============================================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

#==============================================================================
# ONYX SDK - CRITICAL: Reflection-heavy, JNI callbacks
#==============================================================================
# The Onyx SDK uses reflection and native code to call these classes
# RawInputCallback is invoked by native stylus driver via reflection
-keep class com.onyx.android.sdk.** { *; }
-keep interface com.onyx.android.sdk.** { *; }

# Keep all callback interfaces that SDK invokes
-keep class * extends com.onyx.android.sdk.pen.RawInputCallback { *; }
-keep class * implements com.onyx.android.sdk.pen.RawInputCallback { *; }

# TouchHelper and related pen classes
-keep class com.onyx.android.sdk.pen.TouchHelper { *; }
-keep class com.onyx.android.sdk.pen.data.** { *; }
-keep class com.onyx.android.sdk.data.note.** { *; }

# EpdController for e-ink display control
-keep class com.onyx.android.sdk.api.device.epd.** { *; }
-keep class com.onyx.android.sdk.device.** { *; }

# RxManager used by SDK
-keep class com.onyx.android.sdk.rx.** { *; }

#==============================================================================
# JNI / NATIVE CODE - BooxNative FFI
#==============================================================================
# Keep JNI methods and the classes they reference
-keep class com.boox.day.booxnotes.BooxNative { *; }
-keep class com.boox.day.booxnotes.BooxBitmap { *; }

# External functions are called from Rust FFI
-keepclassmembers class com.boox.day.booxnotes.BooxNative {
    native <methods>;
}

#==============================================================================
# ROOM DATABASE - Entities, DAOs, TypeConverters
#==============================================================================
# Room uses reflection for entity instantiation
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep all database entities
-keep class com.boox.day.data.db.DayPage { *; }
-keep class com.boox.day.data.db.CalendarEvent { *; }
-keep class com.boox.day.data.db.BooxNote { *; }
-keep class com.boox.day.data.db.BooxNotePage { *; }
-keep class com.boox.day.data.db.Kv { *; }
-keep class com.boox.day.data.db.Attachment { *; }
-keep class com.boox.day.data.db.Image { *; }
-keep class com.boox.day.data.db.Folder { *; }
-keep class com.boox.day.data.db.Notebook { *; }
-keep class com.boox.day.data.db.Stroke { *; }
-keep class com.boox.day.data.db.Page { *; }
-keep class com.boox.day.data.db.TextAnnotation { *; }
-keep class com.boox.day.data.db.Task { *; }
-keep class com.boox.day.data.db.BooxNoteIndex { *; }
-keep class com.boox.day.data.db.BooxNoteLink { *; }
-keep class com.boox.day.data.db.LocalEvent { *; }
-keep class com.boox.day.data.db.LocalCalendar { *; }
-keep class com.boox.day.data.db.SummaryCache { *; }
-keep class com.boox.day.data.db.ConnectedAccountEntity { *; }
-keep class com.boox.day.data.db.CalendarSelectionEntity { *; }
-keep class com.boox.day.data.db.UnifiedOcrPage { *; }
-keep class com.boox.day.data.db.UnifiedOcrText { *; }
-keep class com.boox.day.data.db.EntityCooccurrence { *; }
-keep class com.boox.day.data.db.UserActivityPattern { *; }
-keep class com.boox.day.data.db.WhatsNewMessage { *; }

# Keep DAOs
-keep class com.boox.day.data.db.*Dao { *; }

# TypeConverters
-keep class com.boox.day.data.db.Converters { *; }

#==============================================================================
# GSON / RETROFIT - JSON Serialization
#==============================================================================
# Gson uses reflection to serialize/deserialize — R8 cannot trace it
-keepattributes Signature
-keepattributes *Annotation*

# Keep @SerializedName annotated fields across the entire codebase
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all API model classes (used with Retrofit/Gson)
-keep class com.boox.day.api.** { *; }

# AI module — all inner data classes are deserialized via Gson reflection
# Covers: TaskAiService, VoiceEventParser, VoicePreferencesParser,
# TextInferenceService, MemoContextExtractor, MemoToTasksService,
# AgenticPlanningService and all their private response/request classes
-keep class com.boox.day.ai.** { *; }

# AI data models (AgenticPlanResponse, AiPreferences, etc.)
-keep class com.boox.day.data.ai.** { *; }

# BooxNoteIndexer inner classes (LlmMatchItem used by Gson)
-keep class com.boox.day.booxnotes.BooxNoteIndexer$* { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

#==============================================================================
# KOTLINX SERIALIZATION
#==============================================================================
# Keep @Serializable classes and their generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializable data model classes
-keep class com.boox.day.data.datastore.AppSettings { *; }
-keep class com.boox.day.data.datastore.EditorSettingCacheManager$* { *; }
-keep class com.boox.day.data.model.ParsedEventData { *; }
-keep class com.boox.day.data.model.RichTextSpan { *; }
-keep class com.boox.day.editor.utils.PenKt$* { *; }

# OCR models — @Serializable enums and data classes used in Room converters
-keep class com.boox.day.ocr.model.** { *; }
-keep class com.boox.day.ocr.semantic.classification.DictionaryLoader$* { *; }
-keep class com.boox.day.ocr.semantic.classification.ConceptBridgeLoader$* { *; }

# Sync — backup schema models (kotlinx.serialization)
-keep class com.boox.day.sync.BackupSchema { *; }
-keep class com.boox.day.sync.BackupSchema$* { *; }
-keep class com.boox.day.sync.FullDatabaseBackupMetadata { *; }

# Keep serializer companions
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializers
-keep,includedescriptorclasses class com.boox.day.**$$serializer { *; }
-keepclassmembers class com.boox.day.** {
    *** Companion;
}
-keepclasseswithmembers class com.boox.day.** {
    kotlinx.serialization.KSerializer serializer(...);
}

#==============================================================================
# WORKMANAGER WORKERS - Instantiated via reflection by WorkerFactory
#==============================================================================
-keep class com.boox.day.notifications.DueDateNotificationWorker { *; }
-keep class com.boox.day.notifications.EventNotificationWorker { *; }
-keep class com.boox.day.ocr.semantic.EventSemanticIndexingWorker { *; }
-keep class com.boox.day.sync.SyncWorker { *; }

#==============================================================================
# MANIFEST COMPONENTS - Instantiated by Android framework via reflection
#==============================================================================
# BroadcastReceivers registered in AndroidManifest.xml
-keep class com.boox.day.notifications.BootReceiver { *; }
-keep class com.boox.day.notifications.DueDateAlarmReceiver { *; }
-keep class com.boox.day.notifications.EventAlarmReceiver { *; }
-keep class com.boox.day.notifications.TimeZoneChangeReceiver { *; }

# FileProvider
-keep class com.boox.day.editor.utils.Provider { *; }

#==============================================================================
# GOOGLE CALENDAR API
#==============================================================================
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.calendar.** { *; }
-keep class com.google.http.** { *; }

# Google Auth
-keep class com.google.android.gms.** { *; }

#==============================================================================
# MUPDF - PDF RENDERING
#==============================================================================
-keep class com.artifex.mupdf.** { *; }

#==============================================================================
# COIL IMAGE LOADING
#==============================================================================
-keep class coil.** { *; }

#==============================================================================
# RXJAVA (used by Onyx SDK)
#==============================================================================
-keep class io.reactivex.** { *; }
-dontwarn io.reactivex.**

#==============================================================================
# KOTLIN COROUTINES
#==============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

#==============================================================================
# JETPACK COMPOSE
#==============================================================================
# Compose compiler generates code that R8 needs to see
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

#==============================================================================
# GENERAL KOTLIN
#==============================================================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

#==============================================================================
# SUPPRESS WARNINGS
#==============================================================================
-dontwarn org.joda.convert.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn sun.misc.Unsafe

#==============================================================================
# LOG STRIPPING - Remove verbose/debug logs in release builds
#==============================================================================
# Strip Log.d (debug) and Log.v (verbose) calls in release builds.
# This removes:
#   - Development debugging output
#   - Verbose diagnostic information
#   - Any PII that was logged at these levels
#
# Keeping: Log.i (info), Log.w (warn), Log.e (error) for production diagnostics
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

#==============================================================================
# MICROSOFT IDENTITY (MSAL) - OAuth for Outlook
#==============================================================================
# MSAL references optional foldable device APIs and FindBugs annotations
# These are runtime optional - suppress missing class errors
-dontwarn com.microsoft.device.display.DisplayMask
-dontwarn com.microsoft.device.display.**
-dontwarn edu.umd.cs.findbugs.annotations.NonNull
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn edu.umd.cs.findbugs.annotations.**
-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.identity.common.** { *; }

#==============================================================================
# OPENTELEMETRY - Used by some dependencies
#==============================================================================
# AutoValue is compile-time only annotation processor, not needed at runtime
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.**
-dontwarn io.opentelemetry.**

# R8 generated missing rules
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder

#==============================================================================
# LZ4 COMPRESSION - Used by SB1 stroke encoding
#==============================================================================
# LZ4Factory uses reflection to discover compressor/decompressor implementations
-keep class net.jpountz.lz4.** { *; }
-keep class net.jpountz.xxhash.** { *; }

#==============================================================================
# GOOGLE DRIVE API - Cloud backup
#==============================================================================
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.**

# SDK 1.5.8 optional references retained by the SDK-wide keep rule above.
# BooxDay does not use the SDK Wi-Fi UI, fastjson 1.x compatibility,
# Apache LDAP hostname parsing or Kerberos/SPNEGO authentication.
# Keep these scoped to R8's reported classes; never suppress all missing classes.
-dontwarn com.alibaba.fastjson.JSONArray
-dontwarn com.alibaba.fastjson.JSONObject
-dontwarn com.onyx.android.sdk.BR
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
