package com.boox.day.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenRouterService {

    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletion(
        @Body request: RawVisionChatRequest
    ): Response<ChatResponse>
}
