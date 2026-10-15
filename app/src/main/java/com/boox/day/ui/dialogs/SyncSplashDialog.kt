package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * Splash dialog shown while Google Calendar is syncing on startup.
 * E-ink friendly: black/white theme, no ripple effects, simple borders.
 */
@Composable
fun SyncSplashDialog() {
    val jokes = remember {
        listOf(
            "Why did the calendar get promoted? It had a lot of great dates.",
            "I tried to make a calendar joke, but it was about time.",
            "Why did the scarecrow become a calendar model? He was outstanding in his field all year.",
            "My calendar is full of holes. I guess that's what happens when you keep making dates.",
            "I'm reading a book about anti-gravity. It's impossible to put down on my schedule.",
            "Why did the computer look at the calendar? It wanted to check its date-a-bits.",
            "I only tell jokes on days that end in \"y\". Good thing the calendar is on my side.",
            "The calendar and the clock broke up. They said it was just a matter of time.",
            "I tried to organize a hide-and-seek tournament, but good players are hard to find in the calendar.",
            "My planner asked for a break. I guess even schedules need some downtime.",
            "Why don't calendars ever get lost? They always know what day it is.",
            "I penciled in a nap today. Best appointment on my calendar.",
            "Why was the calendar always calm? It took things one day at a time.",
            "My calendar and I had a talk. We decided to make more time for each other.",
            "I bought a calendar for my dog. Now every day is a paws-itively great date.",
            "The calendar started a band. They called it \"The Timekeepers.\"",
            "Why did the calendar bring a ladder? It wanted to stay on top of its dates.",
            "I told my calendar a secret. Now it's full of classified dates.",
            "The calendar went to therapy. It had too many issues.",
            "Why did the calendar join the gym? It wanted stronger dates.",
            "My calendar and coffee mug are best friends. They both live for busy mornings.",
            "The calendar tried stand-up comedy. It really knew how to time its jokes.",
            "I asked my calendar for advice. It said, \"Take it one box at a time.\"",
            "Why did the calendar fail the test? It had too many blank days.",
            "My calendar loves road trips. It's always marking important dates.",
            "The calendar opened a restaurant. It serves lunch dates only.",
            "I tried to argue with my calendar, but it already had everything planned out.",
            "The calendar became a teacher. It really knows how to schedule lessons.",
            "Why did the calendar get invited to all the parties? It had a lot of popular dates.",
            "My calendar doesn't like surprises. It prefers everything penciled in.",
            "The calendar wrote a book. It's a real page-turner about time.",
            "Why did the calendar smile at midnight? It was looking forward to a new day."
        )
    }

    val joke = remember { jokes.random() }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    // Non-dismissable popup for sync progress
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Syncing Calendar...",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Here's a light calendar joke while we sync your calendar...",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = joke,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

