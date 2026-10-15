package com.boox.day.api.ai

/**
 * Capabilities that AI models may support.
 * Used for compile-time validation when building requests.
 */
enum class AiCapability {
    /** Model can process base64-encoded images in requests */
    VISION,

    /** Model can process base64-encoded audio in requests */
    AUDIO,

    /** Model accepts text-only input (may be faster than vision models) */
    TEXT,

    /** Model supports response_format: json_object for structured output */
    JSON_OUTPUT,

    /** Model supports extended thinking via extra_body.reasoning */
    REASONING,

    /** Model is optimized for low latency responses */
    FAST
}
