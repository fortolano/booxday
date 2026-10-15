package com.boox.day.api

import com.boox.day.BuildConfig
import com.boox.day.security.UserOpenRouterKey
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * OpenRouter API client.
 *
 * AI runs only on the user's own OpenRouter key (Oct 2026, both flavors): no build key, no
 * server-delivered key. The key is read at request time, so a newly saved key works at once.
 */
object OpenRouterClient {

    private const val BASE_URL = "https://openrouter.ai/api/v1/"

    /** The user's own key, or "" when none is saved (AI features then ask for one). */
    fun getActiveApiKey(): String = UserOpenRouterKey.value.orEmpty()

    /** True when the user has saved an OpenRouter key. */
    fun hasApiKey(): Boolean = UserOpenRouterKey.hasKey

    // Dynamic auth interceptor - resolves key at request time
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${getActiveApiKey()}")
            .build()
        chain.proceed(request)
    }

    // SECURITY: Only log in debug builds to prevent sensitive data exposure
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC  // Log method, URL, response code only
        } else {
            HttpLoggingInterceptor.Level.NONE   // No logging in release
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)  // Longer for vision models
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: OpenRouterService = retrofit.create(OpenRouterService::class.java)
}
