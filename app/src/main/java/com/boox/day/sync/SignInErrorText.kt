package com.boox.day.sync

import com.boox.day.api.NetworkState
import com.boox.day.api.NetworkStatus
import com.boox.day.api.ai.AiProblem

/**
 * Google sign-in failures in the user's words (Oct 2026). Before this the calendar accounts showed
 * "Sign-in failed: Sign-in failed: 10: " (reported 21 Jan), a cancelled sign-in showed nothing (a
 * sign-in "loop", reported 14 Dec), and a failed Google Drive sign-in for backups showed nothing.
 * Codes are GoogleSignInStatusCodes / CommonStatusCodes.
 */
object SignInErrorText {
    const val NETWORK_ERROR = 7
    const val DEVELOPER_ERROR = 10
    const val SIGN_IN_FAILED = 12500
    const val CANCELLED = 12501
    const val IN_PROGRESS = 12502

    /** The text for a Google sign-in that ended with [statusCode] (null: no code, an unexpected error). */
    fun google(statusCode: Int?, network: NetworkState = NetworkStatus.current()): String = when {
        statusCode == CANCELLED -> "Sign-in was cancelled."
        statusCode == IN_PROGRESS -> "Sign-in is already in progress."
        // Offline, any other failure is about the connection: the same words as the AI features
        network == NetworkState.NONE -> AiProblem.NO_NETWORK.message
        network == NetworkState.NO_INTERNET -> AiProblem.NO_INTERNET.message
        statusCode == NETWORK_ERROR -> "Couldn't reach Google. Check the internet connection, then try again."
        statusCode == DEVELOPER_ERROR ->
            "Google didn't accept BooxDay's sign-in (code 10). This is a setup problem in BooxDay, " +
                "not your account. Please report it on GitHub."
        statusCode == SIGN_IN_FAILED ->
            "Google sign-in failed. Turn on Google Play in the BOOX settings and make sure Google Play " +
                "services is up to date, then try again."
        statusCode == null -> "Google sign-in failed."
        else -> "Google sign-in failed (code $statusCode)."
    }
}
