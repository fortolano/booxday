package com.boox.day

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.lifecycleScope
import com.boox.day.api.OpenRouterConnect
import kotlinx.coroutines.launch

/**
 * Opened by the Connect OpenRouter return page's button (`booxday-connect://openrouter?code=...`,
 * Oct 2026). Its own small screen, not MainActivity: MainActivity's standard launch mode would start
 * a second copy of the main screen and its canvas. Trades the code for a key, then brings the app
 * back, where the OpenRouter key card shows the result ([OpenRouterConnect.status]).
 */
class OpenRouterConnectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Connecting to OpenRouter..."
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
        })

        // The exchange runs on its own scope, so it finishes even if this screen closes early
        val finishing = OpenRouterConnect.finishFromLinkAsync(applicationContext, intent?.dataString)
        lifecycleScope.launch {
            finishing.await()
            startActivity(backToApp())
            finish()
        }
    }

    /** The running app, brought forward (its existing MainActivity, not a new one). */
    @OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    private fun backToApp(): Intent =
        Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
}
