package com.boox.day.api.ai

/**
 * Message types for AI chat requests.
 * Sealed class ensures type-safe message construction.
 */
sealed class AiMessage {
    abstract val role: String

    /**
     * System message that sets the behavior/context for the AI.
     * Usually the first message in a conversation.
     */
    data class System(val content: String) : AiMessage() {
        override val role = "system"
    }

    /**
     * Text-only user message.
     */
    data class User(val content: String) : AiMessage() {
        override val role = "user"
    }

    /**
     * User message with both text and an image.
     * Requires model with VISION capability.
     *
     * @param text The text prompt accompanying the image
     * @param imageBase64 Base64-encoded image (PNG or JPEG)
     */
    data class UserWithImage(
        val text: String,
        val imageBase64: String
    ) : AiMessage() {
        override val role = "user"
    }

    /**
     * User message with both text and audio.
     * Requires model with AUDIO capability (e.g., Gemini 2.0 Flash).
     *
     * @param text The text prompt accompanying the audio
     * @param audioBase64 Base64-encoded audio data
     * @param mimeType Audio MIME type (e.g., "audio/wav", "audio/mp3", "audio/webm")
     */
    data class UserWithAudio(
        val text: String,
        val audioBase64: String,
        val mimeType: String = "audio/wav"
    ) : AiMessage() {
        override val role = "user"
    }

    /**
     * Assistant message for few-shot examples or conversation history.
     */
    data class Assistant(val content: String) : AiMessage() {
        override val role = "assistant"
    }
}
