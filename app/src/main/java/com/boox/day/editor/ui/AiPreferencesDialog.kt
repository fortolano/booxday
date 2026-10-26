package com.boox.day.editor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.boox.day.R
import com.boox.day.ai.VoicePreferencesParser
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.CommuteStyle
import com.boox.day.data.ai.EnergyPattern
import com.boox.day.data.ai.FocusStyle
import com.boox.day.data.ai.Industry
import com.boox.day.data.ai.Interest
import com.boox.day.data.ai.InterestCategory
import com.boox.day.data.ai.LifeSituation
import com.boox.day.data.ai.LivingEnvironment
import com.boox.day.data.ai.PlanningHorizon
import com.boox.day.data.ai.Profession
import com.boox.day.data.ai.RoutinePreference
import com.boox.day.data.ai.SleepSchedule
import com.boox.day.data.ai.StressResponse
import com.boox.day.data.ai.SuggestionCount
import com.boox.day.data.ai.SuggestionStyle
import com.boox.day.speech.AudioRecorder
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.ui.components.WaveformCanvas
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.speech.VoiceInputState
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AI Preferences Dialog - "Getting to Know You" Wizard
 *
 * A guided, conversational flow for personalizing Smart Suggestions.
 *
 * Features:
 * - 5 tabs with NEXT/BACK navigation
 * - Progress indicator (step dots)
 * - Voice input as prominent first option
 * - ModernDialog styling (soft gray palette)
 * - Contextual help text explaining benefits
 * - "Pick Top 3" priorities instead of 7 sliders
 *
 * Tabs:
 * 1. Priorities - Pick top 3 life areas
 * 2. About Me - Life situation, energy pattern
 * 3. Daily Rhythm - Sleep, focus, commute, routine, stress
 * 4. Lifestyle - Traits, situations, interests
 * 5. AI Style - Suggestion preferences
 *
 * E-Ink optimized: Uses Popup, noRippleClickable, EInkTheme.
 */

private enum class PreferencesTab(val displayName: String, val stepNumber: Int) {
    Priorities("Priorities", 1),
    AboutMe("About Me", 2),
    DailyRhythm("Daily Rhythm", 3),
    Lifestyle("Lifestyle", 4),
    AiStyle("AI Style", 5)
}

/**
 * What closing the wizard saves (Oct 2026): the X, the back key and a tap outside keep the
 * answers, through the same save as the Save button. Null when nothing changed: nothing is written.
 */
internal fun preferencesToSaveOnClose(initial: AiPreferences, current: AiPreferences): AiPreferences? =
    if (current == initial) null else current.copy(setupComplete = true)

@Composable
fun AiPreferencesDialog(
    initialPreferences: AiPreferences,
    onSave: (AiPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(PreferencesTab.Priorities) }
    var preferences by remember { mutableStateOf(initialPreferences) }
    val close = {
        val toSave = preferencesToSaveOnClose(initialPreferences, preferences)
        if (toSave != null) onSave(toSave) else onDismiss()
    }

    // Voice input state
    var showVoiceInput by remember { mutableStateOf(false) }

    // Show voice input popup
    if (showVoiceInput) {
        VoicePreferencesInputPopup(
            onPreferencesExtracted = { newPreferences ->
                preferences = newPreferences
                showVoiceInput = false
            },
            onDismiss = { showVoiceInput = false }
        )
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = close,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .fillMaxHeight(0.85f)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header with title, voice button, and close
                DialogHeader(
                    // Voice setup sends audio to AI: needs the user's own OpenRouter key (Oct 2026)
                    onVoiceClick = { if (com.boox.day.ui.AiKeyGate.requireKey()) showVoiceInput = true },
                    onClose = close
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress indicator
                ProgressIndicator(
                    currentStep = selectedTab.stepNumber,
                    totalSteps = PreferencesTab.entries.size
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Row
                TabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        PreferencesTab.Priorities -> PrioritiesTabContent(
                            preferences = preferences,
                            onPreferencesChanged = { preferences = it }
                        )
                        PreferencesTab.AboutMe -> AboutMeTabContent(
                            preferences = preferences,
                            onPreferencesChanged = { preferences = it }
                        )
                        PreferencesTab.DailyRhythm -> DailyRhythmTabContent(
                            preferences = preferences,
                            onPreferencesChanged = { preferences = it }
                        )
                        PreferencesTab.Lifestyle -> LifestyleTabContent(
                            preferences = preferences,
                            onPreferencesChanged = { preferences = it }
                        )
                        PreferencesTab.AiStyle -> AiStyleTabContent(
                            preferences = preferences,
                            onPreferencesChanged = { preferences = it }
                        )
                    }
                }

                // Navigation Bar (Back / Skip / Next)
                NavigationBar(
                    currentTab = selectedTab,
                    onBack = {
                        val currentIndex = PreferencesTab.entries.indexOf(selectedTab)
                        if (currentIndex > 0) {
                            selectedTab = PreferencesTab.entries[currentIndex - 1]
                        }
                    },
                    onNext = {
                        val currentIndex = PreferencesTab.entries.indexOf(selectedTab)
                        if (currentIndex < PreferencesTab.entries.size - 1) {
                            selectedTab = PreferencesTab.entries[currentIndex + 1]
                        }
                    },
                    onSave = { onSave(preferences.copy(setupComplete = true)) },
                    isLastTab = selectedTab == PreferencesTab.AiStyle
                )
            }
        }
    }
}

// ============================================================
// HEADER COMPONENTS
// ============================================================

@Composable
private fun DialogHeader(
    onVoiceClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ai_sparkles),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = EInkTheme.ModernDialog.titleColor
            )
            Text(
                text = "AI Preferences",
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.ModernDialog.titleColor
            )
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice input button (prominent) - Hidden for MVP release
            if (false) Box(
                modifier = Modifier
                    .height(40.dp)
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.Colors.darkGray,
                        RoundedCornerShape(4.dp)
                    )
                    .noRippleClickable(onClick = onVoiceClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Voice Setup",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }

            // Close button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        0.5.dp,
                        EInkTheme.Colors.borderStrong,
                        RoundedCornerShape(4.dp)
                    )
                    .noRippleClickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(20.dp),
                    tint = EInkTheme.Colors.gray
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val stepNum = index + 1
            val isCompleted = stepNum < currentStep
            val isCurrent = stepNum == currentStep

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .background(
                        when {
                            isCurrent -> EInkTheme.Colors.darkGray
                            isCompleted -> EInkTheme.Colors.darkGray
                            else -> EInkTheme.Colors.lightGray
                        },
                        CircleShape
                    )
            )

            if (index < totalSteps - 1) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun TabRow(
    selectedTab: PreferencesTab,
    onTabSelected: (PreferencesTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                EInkTheme.Colors.borderStrong,
                RoundedCornerShape(4.dp)
            )
            .clip(RoundedCornerShape(4.dp))
    ) {
        PreferencesTab.entries.forEachIndexed { index, tab ->
            val isSelected = selectedTab == tab

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) EInkTheme.Colors.darkGray
                        else EInkTheme.Colors.background
                    )
                    .noRippleClickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.displayName,
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.darkGray
                )
            }

            // Divider between tabs
            if (index < PreferencesTab.entries.size - 1) {
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(36.dp)
                        .background(EInkTheme.Colors.borderStrong)
                )
            }
        }
    }
}

@Composable
private fun NavigationBar(
    currentTab: PreferencesTab,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    isLastTab: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button (40%)
        Box(modifier = Modifier.weight(0.4f)) {
            if (currentTab != PreferencesTab.Priorities) {
                Box(
                    modifier = Modifier
                        .height(EInkTheme.Global.touchTarget)
                        .border(
                            EInkTheme.ModernDialog.borderWidth,
                            EInkTheme.Colors.gray,
                            RoundedCornerShape(4.dp)
                        )
                        .noRippleClickable(onClick = onBack)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EInkTheme.Colors.darkGray
                        )
                        Text(
                            text = "Back",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        }

        // Spacer (20%)
        Spacer(modifier = Modifier.weight(0.2f))

        // Next/Save button (40%)
        Box(
            modifier = Modifier.weight(0.4f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .height(EInkTheme.Global.touchTarget)
                    .background(
                        EInkTheme.ModernDialog.PrimaryButton.background,
                        RoundedCornerShape(4.dp)
                    )
                    .noRippleClickable(onClick = if (isLastTab) onSave else onNext)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isLastTab) "Save" else "Next",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.ModernDialog.PrimaryButton.textColor
                    )
                    if (!isLastTab) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EInkTheme.ModernDialog.PrimaryButton.textColor
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// PRIORITIES TAB - "Pick Top 3"
// ============================================================

private data class PriorityItem(
    val key: String,
    val label: String,
    val description: String
)

private val priorityItems = listOf(
    PriorityItem("work", "Work & Career", "Job tasks, projects, meetings"),
    PriorityItem("education", "Education", "Learning, courses, studying"),
    PriorityItem("family", "Family", "Family time, relationships"),
    PriorityItem("health", "Health", "Exercise, wellness, self-care"),
    PriorityItem("social", "Social", "Friends, networking, events"),
    PriorityItem("hobbies", "Hobbies", "Creative pursuits, recreation"),
    PriorityItem("finance", "Finance", "Budget, investments, planning"),
    PriorityItem("home", "Home & Organization", "Chores, errands, organizing"),
    PriorityItem("growth", "Personal Growth", "Goals, habits, self-improvement")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrioritiesTabContent(
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit
) {
    // Track selected priorities (those with value 5)
    val selectedPriorities = remember(preferences) {
        mutableSetOf<String>().apply {
            if (preferences.workPriority == 5) add("work")
            if (preferences.educationPriority == 5) add("education")
            if (preferences.familyPriority == 5) add("family")
            if (preferences.healthPriority == 5) add("health")
            if (preferences.socialPriority == 5) add("social")
            if (preferences.hobbyPriority == 5) add("hobbies")
            if (preferences.financePriority == 5) add("finance")
            if (preferences.homePriority == 5) add("home")
            if (preferences.growthPriority == 5) add("growth")
        }
    }

    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Welcome intro section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ai_sparkles),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Let's personalize your experience",
                        fontSize = EInkTheme.Typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                Text(
                    text = "Answer a few quick questions so I can give you task suggestions that actually fit your life. This takes about 2 minutes.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    lineHeight = 22.sp
                )
            }

            // Priority selection card
            QuestionCard(
                title = "What matters most to you right now?",
                subtitle = "Select up to 3 areas to focus on",
                helpText = "I'll prioritize suggestions in these areas. You can always change this later."
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    priorityItems.forEach { item ->
                        val isSelected = selectedPriorities.contains(item.key)
                        val canSelect = selectedPriorities.size < 3 || isSelected

                        PriorityChip(
                            label = item.label,
                            description = item.description,
                            isSelected = isSelected,
                            enabled = canSelect,
                            onClick = {
                                val newSelected = selectedPriorities.toMutableSet()
                                if (isSelected) {
                                    newSelected.remove(item.key)
                                } else if (newSelected.size < 3) {
                                    newSelected.add(item.key)
                                }

                                // Update preferences based on selection
                                onPreferencesChanged(
                                    preferences.copy(
                                        workPriority = if (newSelected.contains("work")) 5 else 3,
                                        educationPriority = if (newSelected.contains("education")) 5 else 3,
                                        familyPriority = if (newSelected.contains("family")) 5 else 3,
                                        healthPriority = if (newSelected.contains("health")) 5 else 3,
                                        socialPriority = if (newSelected.contains("social")) 5 else 3,
                                        hobbyPriority = if (newSelected.contains("hobbies")) 5 else 3,
                                        financePriority = if (newSelected.contains("finance")) 5 else 3,
                                        homePriority = if (newSelected.contains("home")) 5 else 3,
                                        growthPriority = if (newSelected.contains("growth")) 5 else 3
                                    )
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selection count - more prominent
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (selectedPriorities.isEmpty())
                            "Tap to select your top priorities"
                        else "${selectedPriorities.size} of 3 selected",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = when {
                            selectedPriorities.size == 3 -> EInkTheme.Colors.darkGray
                            selectedPriorities.isEmpty() -> EInkTheme.Colors.gray
                            else -> EInkTheme.Colors.gray
                        },
                        fontWeight = if (selectedPriorities.size == 3) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }

            // How many suggestions question
            QuestionCard(
                title = "How many suggestions do you want?",
                subtitle = "Smart Suggestions generates personalized task ideas based on your calendar, notes, and preferences.",
                helpText = "More suggestions give you options. Fewer keeps things focused."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionCount.entries.forEach { count ->
                        SelectableOptionCard(
                            label = count.displayName,
                            description = null,
                            isSelected = preferences.suggestionCount == count,
                            onClick = { onPreferencesChanged(preferences.copy(suggestionCount = count)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(
    label: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> EInkTheme.Colors.darkGray
        !enabled -> EInkTheme.Colors.offWhite
        else -> EInkTheme.Colors.background
    }
    val textColor = when {
        isSelected -> EInkTheme.Colors.white
        !enabled -> EInkTheme.Colors.lightGray
        else -> EInkTheme.Colors.darkGray
    }
    val borderColor = when {
        isSelected -> EInkTheme.Colors.darkGray
        !enabled -> EInkTheme.Colors.borderStrong
        else -> EInkTheme.Colors.gray
    }

    Box(
        modifier = Modifier
            .height(EInkTheme.Global.touchTarget)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(EInkTheme.ModernDialog.borderWidth, borderColor, RoundedCornerShape(4.dp))
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = EInkTheme.Colors.white
                )
            }
            Column {
                Text(
                    text = label,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

// ============================================================
// ABOUT ME TAB
// ============================================================

@Composable
private fun AboutMeTabContent(
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Life Situation (multi-select, limit 2)
            QuestionCard(
                title = "What describes you best?",
                subtitle = "Select up to 2 that apply",
                helpText = "Many people wear multiple hats. A working parent or student with a side job gets better suggestions when we know both roles."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LifeSituation.entries.forEach { situation ->
                        val isSelected = preferences.lifeSituations.contains(situation)
                        val canSelect = preferences.lifeSituations.size < 2 || isSelected

                        SelectableOptionCard(
                            label = situation.displayName,
                            description = situation.description,
                            isSelected = isSelected,
                            enabled = canSelect,
                            onClick = {
                                val newSituations = preferences.lifeSituations.toMutableSet()
                                if (isSelected) {
                                    newSituations.remove(situation)
                                } else if (newSituations.size < 2) {
                                    newSituations.add(situation)
                                }
                                onPreferencesChanged(preferences.copy(lifeSituations = newSituations))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Selection count indicator
                    Text(
                        text = when (preferences.lifeSituations.size) {
                            0 -> "Tap to select"
                            1 -> "1 of 2 selected (you can add another)"
                            else -> "2 of 2 selected"
                        },
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Energy Pattern
            QuestionCard(
                title = "When are you most productive?",
                subtitle = "Everyone has different energy rhythms throughout the day.",
                helpText = "Morning people get big tasks early. Night owls get them later. I'll match your natural flow."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EnergyPattern.entries.forEach { pattern ->
                        SelectableOptionCard(
                            label = pattern.displayName,
                            description = pattern.description,
                            isSelected = preferences.energyPattern == pattern,
                            onClick = { onPreferencesChanged(preferences.copy(energyPattern = pattern)) }
                        )
                    }
                }
            }

            // Living Environment
            QuestionCard(
                title = "Where do you live?",
                subtitle = "City life, suburbs, and rural areas offer different opportunities.",
                helpText = "City dwellers might get 'walk to that new café' while rural folks get 'enjoy the quiet outdoors.'"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivingEnvironment.entries.forEach { env ->
                        SelectableOptionCard(
                            label = env.displayName,
                            description = env.description,
                            isSelected = preferences.livingEnvironment == env,
                            onClick = { onPreferencesChanged(preferences.copy(livingEnvironment = env)) }
                        )
                    }
                }
            }

            // Industry
            QuestionCard(
                title = "What industry are you in?",
                subtitle = "This helps me suggest tasks that fit your world.",
                helpText = "Your industry gives me useful context for realistic task ideas."
            ) {
                PreferencesDropdown(
                    selectedLabel = preferences.industry.displayName,
                    options = Industry.entries,
                    optionLabel = { it.displayName },
                    onSelected = { selected ->
                        onPreferencesChanged(preferences.copy(industry = selected))
                    }
                )
            }

            // Profession
            QuestionCard(
                title = "What do you do?",
                subtitle = "I will tailor suggestions to your role.",
                helpText = "This keeps your task plan aligned with your day-to-day work."
            ) {
                PreferencesDropdown(
                    selectedLabel = preferences.profession.displayName,
                    options = Profession.entries,
                    optionLabel = { it.displayName },
                    onSelected = { selected ->
                        onPreferencesChanged(preferences.copy(profession = selected))
                    }
                )
            }
        }
    }
}

// ============================================================
// DAILY RHYTHM TAB (NEW)
// ============================================================

@Composable
private fun DailyRhythmTabContent(
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sleep Schedule
            QuestionCard(
                title = "What's your sleep pattern?",
                subtitle = "Knowing when you wake up and wind down helps me suggest tasks at the right time of day.",
                helpText = "Early birds get morning tasks first. Night owls get evening-friendly suggestions."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SleepSchedule.entries.forEach { schedule ->
                        SelectableOptionCard(
                            label = schedule.displayName,
                            description = schedule.description,
                            isSelected = preferences.sleepSchedule == schedule,
                            onClick = { onPreferencesChanged(preferences.copy(sleepSchedule = schedule)) }
                        )
                    }
                }
            }

            // Focus Style
            QuestionCard(
                title = "How do you prefer to work?",
                subtitle = "Some people thrive with long focus blocks, others prefer switching between tasks.",
                helpText = "Deep focus users get fewer, meatier tasks. Task switchers get bite-sized suggestions."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FocusStyle.entries.forEach { style ->
                        SelectableOptionCard(
                            label = style.displayName,
                            description = style.description,
                            isSelected = preferences.focusStyle == style,
                            onClick = { onPreferencesChanged(preferences.copy(focusStyle = style)) }
                        )
                    }
                }
            }

            // Commute Style
            QuestionCard(
                title = "What's your work arrangement?",
                subtitle = "Your commute and work location affect when and what tasks make sense.",
                helpText = "Remote workers get home-based tasks. Commuters get travel-friendly suggestions."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommuteStyle.entries.forEach { style ->
                        SelectableOptionCard(
                            label = style.displayName,
                            description = style.description,
                            isSelected = preferences.commuteStyle == style,
                            onClick = { onPreferencesChanged(preferences.copy(commuteStyle = style)) }
                        )
                    }
                }
            }

            // Planning Horizon
            QuestionCard(
                title = "How far ahead do you plan?",
                subtitle = "Do you focus on today, or think in terms of the week ahead?",
                helpText = "Day-by-day planners get immediate tasks. Weekly planners get prep suggestions for upcoming events."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlanningHorizon.entries.forEach { horizon ->
                        SelectableOptionCard(
                            label = horizon.displayName,
                            description = horizon.description,
                            isSelected = preferences.planningHorizon == horizon,
                            onClick = { onPreferencesChanged(preferences.copy(planningHorizon = horizon)) }
                        )
                    }
                }
            }

            // Routine Preference
            QuestionCard(
                title = "Routine or spontaneity?",
                subtitle = "Some people love predictable routines, others prefer variety and flexibility.",
                helpText = "Structured users get consistent daily suggestions. Spontaneous users get fresh, varied ideas."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutinePreference.entries.forEach { pref ->
                        SelectableOptionCard(
                            label = pref.displayName,
                            description = pref.description,
                            isSelected = preferences.routinePreference == pref,
                            onClick = { onPreferencesChanged(preferences.copy(routinePreference = pref)) }
                        )
                    }
                }
            }

            // Stress Response
            QuestionCard(
                title = "When stressed, you prefer to...",
                subtitle = "When deadlines pile up, do you make lists and power through, or step back and recharge?",
                helpText = "Organizers get structured task breakdowns. Decompressors get reminders to take breaks first."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StressResponse.entries.forEach { response ->
                        SelectableOptionCard(
                            label = response.displayName,
                            description = response.description,
                            isSelected = preferences.stressResponse == response,
                            onClick = { onPreferencesChanged(preferences.copy(stressResponse = response)) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// LIFESTYLE TAB (Combined: Traits + Situations + Interests)
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LifestyleTabContent(
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Lifestyle Traits
            QuestionCard(
                title = "Select all that describe you",
                subtitle = "Pick the vibes that fit. This helps me suggest things you'll actually enjoy.",
                helpText = "Social butterflies get 'catch up with friends.' Homebodies get 'cozy movie night.'"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToggleOptionCompact(
                        label = "Active Lifestyle",
                        isChecked = preferences.isActiveLifestyle,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isActiveLifestyle = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Weekend Warrior",
                        isChecked = preferences.isWeekendWarrior,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isWeekendWarrior = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Homebody",
                        isChecked = preferences.isHomebody,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isHomebody = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Social Butterfly",
                        isChecked = preferences.isSocialButterfly,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isSocialButterfly = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Quiet Introvert",
                        isChecked = preferences.isQuietIntrovert,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isQuietIntrovert = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Outdoors Lover",
                        isChecked = preferences.isOutdoorsLover,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isOutdoorsLover = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Foodie",
                        isChecked = preferences.isFoodie,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isFoodie = it)) }
                    )
                    ToggleOptionCompact(
                        label = "Digital Native",
                        isChecked = preferences.isDigitalNative,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isDigitalNative = it)) }
                    )
                }
            }

            // Situation Flags
            QuestionCard(
                title = "Your situation",
                subtitle = "Life circumstances that shape your daily responsibilities.",
                helpText = "Parents get school-related reminders. Pet owners get 'time for a walk with the dog.'"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToggleOptionCompact(
                        label = "I have children",
                        isChecked = preferences.hasKids,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(hasKids = it)) }
                    )
                    ToggleOptionCompact(
                        label = "I have pets",
                        isChecked = preferences.hasPets,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(hasPets = it)) }
                    )
                    ToggleOptionCompact(
                        label = "I'm a caregiver",
                        isChecked = preferences.isCareGiver,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(isCareGiver = it)) }
                    )
                    ToggleOptionCompact(
                        label = "I live alone",
                        isChecked = preferences.livesAlone,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(livesAlone = it)) }
                    )
                }
            }

            // Interests
            QuestionCard(
                title = "Your interests",
                subtitle = "Select a few to get more relevant suggestions",
                helpText = "I'll occasionally suggest tasks related to your interests."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InterestCategory.entries.forEach { category ->
                        val categoryInterests = Interest.entries.filter { it.category == category }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = category.displayName,
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.darkGray
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categoryInterests.forEach { interest ->
                                    InterestChipCompact(
                                        interest = interest,
                                        isSelected = preferences.interests.contains(interest),
                                        onClick = {
                                            val newInterests = if (preferences.interests.contains(interest)) {
                                                preferences.interests - interest
                                            } else {
                                                preferences.interests + interest
                                            }
                                            onPreferencesChanged(preferences.copy(interests = newInterests))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InterestChipCompact(
    interest: Interest,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .background(
                if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.Colors.background,
                RoundedCornerShape(4.dp)
            )
            .border(
                EInkTheme.ModernDialog.borderWidth,
                if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray,
                RoundedCornerShape(4.dp)
            )
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = EInkTheme.Colors.white
                )
            }
            Text(
                text = interest.displayName,
                fontSize = EInkTheme.Typography.bodySmall,
                color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.darkGray
            )
        }
    }
}

// ============================================================
// AI STYLE TAB
// ============================================================

@Composable
private fun AiStyleTabContent(
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Suggestion Style
            QuestionCard(
                title = "What kind of suggestions?",
                subtitle = "Choose between purely practical tasks or a mix that includes wellness and self-care reminders.",
                helpText = "Practical focuses on getting things done. Holistic reminds you to take breaks, drink water, and maintain balance."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionStyle.entries.forEach { style ->
                        SelectableOptionCard(
                            label = style.displayName,
                            description = style.description,
                            isSelected = preferences.suggestionStyle == style,
                            onClick = { onPreferencesChanged(preferences.copy(suggestionStyle = style)) }
                        )
                    }
                }
            }

            // Toggles
            QuestionCard(
                title = "Include these types?",
                subtitle = "Beyond your tasks, I can remind you about wellness and relationships.",
                helpText = "Toggle these off if you prefer only task-focused suggestions."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToggleOptionWithDescription(
                        label = "Health Reminders",
                        description = "Exercise, water, breaks",
                        isChecked = preferences.includeHealthReminders,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(includeHealthReminders = it)) }
                    )
                    ToggleOptionWithDescription(
                        label = "Social Reminders",
                        description = "Reach out to friends, family",
                        isChecked = preferences.includeSocialReminders,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(includeSocialReminders = it)) }
                    )
                    ToggleOptionWithDescription(
                        label = "Deadline Warnings",
                        description = "Prep reminders for events",
                        isChecked = preferences.includeDeadlineWarnings,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(includeDeadlineWarnings = it)) }
                    )
                }
            }

            // Special Modes
            QuestionCard(
                title = "Special modes",
                subtitle = "Turn these on temporarily to shift what kinds of suggestions you receive.",
                helpText = "These override your normal preferences. Turn them off when you're back to your regular routine."
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToggleOptionWithDescription(
                        label = "Vacation Mode",
                        description = "Relaxation-focused suggestions",
                        isChecked = preferences.vacationMode,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(vacationMode = it)) }
                    )
                    ToggleOptionWithDescription(
                        label = "Crunch Mode",
                        description = "Deadline-focused, minimal distractions",
                        isChecked = preferences.crunchMode,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(crunchMode = it)) }
                    )
                    ToggleOptionWithDescription(
                        label = "Holiday Mode",
                        description = "Holiday-themed suggestions",
                        isChecked = preferences.holidayMode,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(holidayMode = it)) }
                    )
                }
            }
        }
    }
}

// ============================================================
// SHARED COMPONENTS
// ============================================================

@Composable
private fun QuestionCard(
    title: String,
    subtitle: String? = null,
    helpText: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.background, RoundedCornerShape(6.dp))
            .border(
                0.5.dp,
                EInkTheme.Colors.borderStrong,
                RoundedCornerShape(6.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = title,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = EInkTheme.ModernDialog.titleColor
        )

        // Subtitle
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        // Content
        content()

        // Help text (AI benefit explanation) - prominent, not an afterthought
        if (helpText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                    .border(0.5.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ai_sparkles),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = EInkTheme.Colors.darkGray
                )
                Text(
                    text = helpText,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun SelectableOptionCard(
    label: String,
    description: String?,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else EInkTheme.Colors.background,
                RoundedCornerShape(4.dp)
            )
            .border(
                if (isSelected) 1.dp else 0.5.dp,
                if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.Colors.borderStrong,
                RoundedCornerShape(4.dp)
            )
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(12.dp)
            .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Radio indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    EInkTheme.ModernDialog.RadioIndicator.borderWidth,
                    EInkTheme.ModernDialog.RadioIndicator.borderColor,
                    CircleShape
                )
                .padding(EInkTheme.ModernDialog.RadioIndicator.innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(EInkTheme.ModernDialog.RadioIndicator.fillColor)
                )
            }
        }

        // Text content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = EInkTheme.Colors.primary
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

@Composable
private fun <T> PreferencesDropdown(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .background(EInkTheme.Colors.background, RoundedCornerShape(6.dp))
                .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                .noRippleClickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedLabel,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Select",
                modifier = Modifier.size(20.dp),
                tint = EInkTheme.Colors.darkGray
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(1.dp, EInkTheme.Colors.primary)
                .heightIn(max = 280.dp)
        ) {
            options.forEach { option ->
                val label = optionLabel(option)
                DropdownMenuItem(
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                ) {
                    Text(
                        text = label,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = if (label == selectedLabel) FontWeight.Bold else FontWeight.Normal,
                        color = EInkTheme.Colors.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleOptionCompact(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .noRippleClickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )

        EInkToggle(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ToggleOptionWithDescription(
    label: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = description,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        EInkToggle(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ============================================================
// VOICE PREFERENCES INPUT POPUP
// ============================================================

/**
 * Popup for voice input to describe yourself naturally.
 * Uses VoicePreferencesParser to convert speech to structured preferences.
 */
@Composable
private fun VoicePreferencesInputPopup(
    onPreferencesExtracted: (AiPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Audio recorder
    val audioRecorder = remember { AudioRecorder(context) }
    val recorderState by audioRecorder.state.collectAsState()
    val rmsLevel by audioRecorder.rmsLevel.collectAsState()

    // Processing state
    var isProcessing by remember { mutableStateOf(false) }
    var processingStage by remember { mutableStateOf("") }
    var funMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Review state - show what was understood before applying
    var extractedPreferences by remember { mutableStateOf<AiPreferences?>(null) }
    val isReviewing = extractedPreferences != null

    // Status message
    val statusMessage = when {
        isProcessing -> funMessage.ifEmpty { processingStage.ifEmpty { "Processing..." } }
        errorMessage != null -> errorMessage!!
        !hasPermission -> "Tap to grant microphone permission"
        else -> when (recorderState) {
            is AudioRecorder.State.Idle -> "Tap to start recording"
            is AudioRecorder.State.Recording -> "Listening — describe yourself"
            is AudioRecorder.State.Speaking -> "Hearing you clearly..."
            is AudioRecorder.State.SilenceDetected -> "Finishing up..."
            is AudioRecorder.State.Processing -> "Preparing audio..."
            is AudioRecorder.State.Error -> (recorderState as AudioRecorder.State.Error).message
        }
    }

    // Rotate fun message during processing
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            while (true) {
                delay(4000)
                funMessage = AiFeedbackMessages.getRandomAudioMessage()
            }
        }
    }

    // Auto-complete callback
    LaunchedEffect(Unit) {
        audioRecorder.setOnAutoCompleteListener { audioBase64 ->
            scope.launch {
                isProcessing = true
                funMessage = AiFeedbackMessages.getRandomAudioMessage()
                errorMessage = null

                val result = VoicePreferencesParser.parseAudioInput(
                    audioBase64 = audioBase64,
                    onStageChange = { stage -> processingStage = stage }
                )

                isProcessing = false

                if (result.isSuccess && result.preferences != null) {
                    // Show review state instead of applying immediately
                    extractedPreferences = result.preferences
                } else {
                    errorMessage = result.error ?: "Couldn't understand. Please try again."
                    audioRecorder.clearData()
                }
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.release()
        }
    }

    // Recording state
    val isRecording = recorderState is AudioRecorder.State.Recording ||
            recorderState is AudioRecorder.State.Speaking

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            if (!isProcessing) {
                audioRecorder.release()
                onDismiss()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = !isProcessing
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.width)
                .background(
                    EInkTheme.ModernDialog.background,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .border(
                    EInkTheme.ModernDialog.borderWidth,
                    EInkTheme.ModernDialog.borderColor,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = EInkTheme.ModernDialog.titleColor
                        )
                        Text(
                            text = if (isRecording) "I'm Listening..." else "Voice Setup",
                            fontSize = EInkTheme.ModernDialog.titleFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = EInkTheme.ModernDialog.titleColor
                        )
                    }

                    if (!isProcessing && !isRecording) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    EInkTheme.ModernDialog.borderWidth,
                                    EInkTheme.Colors.lightGray,
                                    RoundedCornerShape(4.dp)
                                )
                                .noRippleClickable {
                                    audioRecorder.release()
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = EInkTheme.Colors.gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Content changes based on state
                if (isReviewing && extractedPreferences != null) {
                    // ═══════════════════════════════════════════════════════════════
                    // REVIEW STATE - Show what was understood
                    // ═══════════════════════════════════════════════════════════════
                    val prefs = extractedPreferences!!

                    ScrollableWithIndicator(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Success header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = EInkTheme.Colors.darkGray
                                )
                                Column {
                                    Text(
                                        text = "Here's what I understood",
                                        fontSize = EInkTheme.Typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    Text(
                                        text = "Review and confirm, or try again",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                            }

                            // Extracted preferences summary
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, EInkTheme.Colors.dividerHairline, RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Life Situation(s)
                                if (prefs.lifeSituations.isNotEmpty()) {
                                    ReviewItem(
                                        label = if (prefs.lifeSituations.size == 1) "Life Situation" else "Life Situations",
                                        value = prefs.lifeSituations.joinToString(" + ") { it.displayName }
                                    )
                                }

                                // Energy Pattern
                                ReviewItem(
                                    label = "Energy Pattern",
                                    value = prefs.energyPattern.displayName
                                )

                                // Living Environment
                                ReviewItem(
                                    label = "Living Environment",
                                    value = prefs.livingEnvironment.displayName
                                )

                                if (prefs.industry != Industry.NOT_SET) {
                                    ReviewItem(
                                        label = "Industry",
                                        value = prefs.industry.displayName
                                    )
                                }

                                if (prefs.profession != Profession.NOT_SET) {
                                    ReviewItem(
                                        label = "Profession",
                                        value = prefs.profession.displayName
                                    )
                                }

                                // Priorities (non-default ones)
                                val priorityList = buildList {
                                    if (prefs.workPriority == 5) add("Work")
                                    if (prefs.healthPriority == 5) add("Health")
                                    if (prefs.familyPriority == 5) add("Family")
                                    if (prefs.socialPriority == 5) add("Social")
                                    if (prefs.educationPriority == 5) add("Education")
                                    if (prefs.financePriority == 5) add("Finance")
                                    if (prefs.hobbyPriority == 5) add("Hobbies")
                                    if (prefs.homePriority == 5) add("Home")
                                    if (prefs.growthPriority == 5) add("Personal Growth")
                                }
                                if (priorityList.isNotEmpty()) {
                                    ReviewItem(
                                        label = "Top Priorities",
                                        value = priorityList.joinToString(", ")
                                    )
                                }

                                // Lifestyle traits
                                val lifestyleList = buildList {
                                    if (prefs.isActiveLifestyle) add("Active lifestyle")
                                    if (prefs.isWeekendWarrior) add("Weekend warrior")
                                    if (prefs.isHomebody) add("Homebody")
                                    if (prefs.isSocialButterfly) add("Social butterfly")
                                    if (prefs.isQuietIntrovert) add("Quiet introvert")
                                    if (prefs.isOutdoorsLover) add("Outdoors lover")
                                    if (prefs.isFoodie) add("Foodie")
                                    if (prefs.isDigitalNative) add("Digital native")
                                }
                                if (lifestyleList.isNotEmpty()) {
                                    ReviewItem(
                                        label = "Lifestyle",
                                        value = lifestyleList.joinToString(", ")
                                    )
                                }

                                // Situation
                                val situationList = buildList {
                                    if (prefs.hasKids) add("Has children")
                                    if (prefs.hasPets) add("Has pets")
                                    if (prefs.isCareGiver) add("Caregiver")
                                    if (prefs.livesAlone) add("Lives alone")
                                }
                                if (situationList.isNotEmpty()) {
                                    ReviewItem(
                                        label = "Situation",
                                        value = situationList.joinToString(", ")
                                    )
                                }

                                // Interests
                                if (prefs.interests.isNotEmpty()) {
                                    ReviewItem(
                                        label = "Interests",
                                        value = prefs.interests.take(5).joinToString(", ") { it.displayName } +
                                                if (prefs.interests.size > 5) " +${prefs.interests.size - 5} more" else ""
                                    )
                                }

                                // Daily Rhythm
                                ReviewItem(
                                    label = "Work Style",
                                    value = "${prefs.focusStyle.displayName}, ${prefs.commuteStyle.displayName}"
                                )

                                ReviewItem(
                                    label = "Sleep Schedule",
                                    value = prefs.sleepSchedule.displayName
                                )
                            }

                            // Note about editing
                            Text(
                                text = "You can fine-tune any of these in the preferences tabs after confirming.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Try Again button
                        Box(
                            modifier = Modifier
                                .weight(0.4f)
                                .height(48.dp)
                                .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(8.dp))
                                .noRippleClickable {
                                    extractedPreferences = null
                                    errorMessage = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Try Again",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.darkGray
                            )
                        }

                        // Looks Good button
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .height(48.dp)
                                .background(EInkTheme.Colors.darkGray, RoundedCornerShape(8.dp))
                                .noRippleClickable {
                                    onPreferencesExtracted(prefs)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = EInkTheme.Colors.white
                                )
                                Text(
                                    text = "Looks Good!",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EInkTheme.Colors.white
                                )
                            }
                        }
                    }
                } else if (isProcessing) {
                    // ═══════════════════════════════════════════════════════════════
                    // PROCESSING STATE
                    // ═══════════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EinkLoadingDotsInline(color = EInkTheme.Colors.darkGray)

                        Text(
                            text = funMessage.ifEmpty { processingStage },
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.darkGray,
                            textAlign = TextAlign.Center
                        )

                        if (processingStage.isNotEmpty() && funMessage.isNotEmpty()) {
                            Text(
                                text = processingStage,
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (isRecording) {
                    // ═══════════════════════════════════════════════════════════════
                    // RECORDING STATE - Show checklist and encouragement
                    // ═══════════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Waveform visualization
                        val voiceState = when (recorderState) {
                            is AudioRecorder.State.Speaking -> VoiceInputState.Speaking
                            is AudioRecorder.State.Recording -> VoiceInputState.Listening
                            is AudioRecorder.State.SilenceDetected -> VoiceInputState.SilenceDetected
                            else -> VoiceInputState.Idle
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                                .border(1.dp, EInkTheme.Colors.dividerHairline, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            WaveformCanvas(
                                rmsLevel = rmsLevel,
                                state = voiceState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .padding(horizontal = 16.dp)
                            )
                        }

                        // Speaking prompts - things to mention
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Things to mention:",
                                fontSize = EInkTheme.Typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.darkGray
                            )
                            VoicePromptItem("Your job or daily role")
                            VoicePromptItem("What's most important to you right now")
                            VoicePromptItem("Morning person or night owl?")
                            VoicePromptItem("Hobbies or interests")
                            VoicePromptItem("Family situation (kids, pets, etc.)")
                        }

                        // Time hint
                        Text(
                            text = "Aim for 30-60 seconds. Tap Stop when done.",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Stop button (large, centered)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(EInkTheme.Colors.darkGray)
                                    .noRippleClickable {
                                        val audio = audioRecorder.stopAndGetBase64()
                                        if (audio != null) {
                                            scope.launch {
                                                isProcessing = true
                                                funMessage = AiFeedbackMessages.getRandomAudioMessage()
                                                errorMessage = null

                                                val result = VoicePreferencesParser.parseAudioInput(
                                                    audioBase64 = audio,
                                                    onStageChange = { stage -> processingStage = stage }
                                                )

                                                isProcessing = false

                                                if (result.isSuccess && result.preferences != null) {
                                                    // Show review state instead of applying immediately
                                                    extractedPreferences = result.preferences
                                                } else {
                                                    errorMessage = result.error ?: "Couldn't understand. Please try again."
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop recording",
                                    modifier = Modifier.size(36.dp),
                                    tint = EInkTheme.Colors.white
                                )
                            }
                        }
                    }
                } else {
                    // ═══════════════════════════════════════════════════════════════
                    // IDLE STATE - Full instructions and script
                    // ═══════════════════════════════════════════════════════════════
                    ScrollableWithIndicator(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Welcome intro
                            Text(
                                text = "Talk to me like you're introducing yourself to a new friend. I'll use what you share to personalize your task suggestions.",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray,
                                lineHeight = 22.sp
                            )

                            // Script box - the main attraction
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                                    .border(1.dp, EInkTheme.Colors.dividerHairline, RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ai_sparkles),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = EInkTheme.Colors.darkGray
                                    )
                                    Text(
                                        text = "Here's a script you can read:",
                                        fontSize = EInkTheme.Typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                }

                                Text(
                                    text = "\"I'm a [your job or role]. Right now, my top priorities are [2-3 things like work, health, family]. I'm a [morning person / night owl] who [works from home / commutes / has a flexible schedule]. I [live alone / have a family / have pets]. In my free time, I enjoy [hobbies].\"",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.gray,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp
                                )
                            }

                            // Examples section
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Example:",
                                    fontSize = EInkTheme.Typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = EInkTheme.Colors.darkGray
                                )

                                Text(
                                    text = "\"I'm a software developer working from home. Health and side projects are my focus right now. I'm a night owl, I have a dog, and I love reading and video games.\"",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.gray,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 20.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(EInkTheme.Colors.background, RoundedCornerShape(4.dp))
                                        .border(1.dp, EInkTheme.Colors.dividerHairline, RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                )
                            }

                            // Tips
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Tips for success:",
                                    fontSize = EInkTheme.Typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = EInkTheme.Colors.darkGray
                                )
                                VoiceTipItem("Speak naturally — no need to be formal")
                                VoiceTipItem("30-60 seconds is perfect")
                                VoiceTipItem("Don't worry about covering everything")
                                VoiceTipItem("You can always redo or edit later")
                            }

                            // Error message if any
                            errorMessage?.let { error ->
                                Text(
                                    text = error,
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.darkGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Start Recording Button - large and inviting
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(EInkTheme.Colors.darkGray, RoundedCornerShape(8.dp))
                            .noRippleClickable {
                                if (!hasPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    errorMessage = null
                                    audioRecorder.startRecording()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = EInkTheme.Colors.white
                            )
                            Text(
                                text = if (!hasPermission) "Tap to Allow Microphone" else "Start Recording",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.white
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePromptItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
    }
}

@Composable
private fun VoiceTipItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = EInkTheme.Colors.gray
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
    }
}

@Composable
private fun ReviewItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End
        )
    }
}
