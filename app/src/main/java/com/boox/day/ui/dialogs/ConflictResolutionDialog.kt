package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.sync.ConflictInfo
import com.boox.day.sync.ConflictResolutionStrategy
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog for resolving sync conflicts between local and remote events.
 *
 * Shows:
 * - Event title and conflicting fields
 * - Local version details
 * - Remote version details
 * - Resolution options
 */
@Composable
fun ConflictResolutionDialog(
    conflict: ConflictInfo,
    onResolve: (ConflictResolutionStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf<ConflictResolutionStrategy?>(null) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
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
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title with warning icon (E-Ink: text symbol)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.warning_40px),
                        contentDescription = "Warning",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = EInkTheme.Global.spacing),
                        tint = EInkTheme.ModernDialog.titleColor
                    )
                    Text(
                        text = "Sync Conflict",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Event title
                Text(
                    text = conflict.localEvent.title,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                // Conflict explanation
                Text(
                    text = "This event was modified both locally and on Google Calendar.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.secondary
                )

                if (conflict.overlappingFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                    Text(
                        text = "Conflicting fields: ${conflict.overlappingFields.joinToString(", ")}",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.secondary
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Side-by-side comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    // Local version (E-Ink: vector drawable icon)
                    VersionCard(
                        title = "Local Version",
                        iconResId = R.drawable.mobile_40px,
                        event = conflict.localEvent,
                        changedFields = conflict.localChangedFields,
                        modifier = Modifier.weight(1f)
                    )

                    // Remote version (E-Ink: vector drawable icon)
                    RemoteVersionCard(
                        title = "Google Version",
                        iconResId = R.drawable.cloud_40px,
                        remoteEvent = conflict.remoteEvent,
                        changedFields = conflict.remoteChangedFields,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Resolution options
                Text(
                    text = "Choose Resolution",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Option: Keep Local (E-Ink: vector drawable icon)
                ResolutionOption(
                    title = "Keep Local Changes",
                    description = "Your changes will overwrite Google Calendar",
                    iconResId = R.drawable.mobile_40px,
                    isSelected = selectedStrategy == ConflictResolutionStrategy.KEEP_LOCAL,
                    onClick = { selectedStrategy = ConflictResolutionStrategy.KEEP_LOCAL }
                )

                // Option: Keep Remote (E-Ink: vector drawable icon)
                ResolutionOption(
                    title = "Use Google Version",
                    description = "Discard local changes, use Google Calendar version",
                    iconResId = R.drawable.cloud_40px,
                    isSelected = selectedStrategy == ConflictResolutionStrategy.KEEP_REMOTE,
                    onClick = { selectedStrategy = ConflictResolutionStrategy.KEEP_REMOTE }
                )

                // Option: Keep Both (E-Ink: vector drawable icon)
                ResolutionOption(
                    title = "Keep Both",
                    description = "Create a local copy, sync with Google version",
                    iconResId = R.drawable.content_copy_40px,
                    isSelected = selectedStrategy == ConflictResolutionStrategy.KEEP_BOTH,
                    onClick = { selectedStrategy = ConflictResolutionStrategy.KEEP_BOTH }
                )

                // Option: Auto-merge (only if possible, E-Ink: vector drawable icon)
                if (conflict.canAutoMerge) {
                    ResolutionOption(
                        title = "Merge Automatically",
                        description = "Combine non-conflicting changes from both",
                        iconResId = R.drawable.refresh_40px,
                        isSelected = selectedStrategy == ConflictResolutionStrategy.AUTO_MERGE,
                        onClick = { selectedStrategy = ConflictResolutionStrategy.AUTO_MERGE }
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Action buttons - 40% / 20% / 40% layout
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.4f)
                    )

                    Spacer(modifier = Modifier.weight(0.2f))

                    // Only enable if a strategy is selected
                    if (selectedStrategy != null) {
                        ActionButton(
                            text = "Resolve",
                            onClick = { selectedStrategy?.let { onResolve(it) } },
                            modifier = Modifier.weight(0.4f)
                        )
                    } else {
                        // Disabled-looking button
                        Box(
                            modifier = Modifier
                                .weight(0.4f)
                                .height(EInkTheme.Button.height)
                                .background(EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Resolve",
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.gray,
                                fontSize = EInkTheme.Typography.button
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing local event version details.
 * E-Ink: Uses text symbol instead of Material Icon.
 */
@Composable
private fun VersionCard(
    title: String,
    iconResId: Int,
    event: com.boox.day.data.db.LocalEvent,
    changedFields: Set<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .padding(EInkTheme.Global.spacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = EInkTheme.Global.spacingSmall),
                tint = EInkTheme.Colors.primary
            )
            Text(
                text = title,
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

        // Title
        FieldRow(
            label = "Title",
            value = event.title,
            isChanged = "title" in changedFields
        )

        // Time
        val timeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        FieldRow(
            label = "Time",
            value = timeFormatter.format(Date(event.startTime)),
            isChanged = "time" in changedFields
        )

        // Location
        if (!event.location.isNullOrBlank() || "location" in changedFields) {
            FieldRow(
                label = "Location",
                value = event.location ?: "(none)",
                isChanged = "location" in changedFields
            )
        }
    }
}

/**
 * Card showing remote event version details.
 * E-Ink: Uses text symbol instead of Material Icon.
 */
@Composable
private fun RemoteVersionCard(
    title: String,
    iconResId: Int,
    remoteEvent: com.google.api.services.calendar.model.Event,
    changedFields: Set<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .padding(EInkTheme.Global.spacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = EInkTheme.Global.spacingSmall),
                tint = EInkTheme.Colors.primary
            )
            Text(
                text = title,
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

        // Title
        FieldRow(
            label = "Title",
            value = remoteEvent.summary ?: "Untitled",
            isChanged = "title" in changedFields
        )

        // Time
        val startMillis = remoteEvent.start?.dateTime?.value ?: remoteEvent.start?.date?.value
        val timeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        FieldRow(
            label = "Time",
            value = startMillis?.let { timeFormatter.format(Date(it)) } ?: "Unknown",
            isChanged = "time" in changedFields
        )

        // Location
        if (!remoteEvent.location.isNullOrBlank() || "location" in changedFields) {
            FieldRow(
                label = "Location",
                value = remoteEvent.location ?: "(none)",
                isChanged = "location" in changedFields
            )
        }
    }
}

/**
 * Row displaying a field with change indicator.
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    isChanged: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.secondary
        )
        Text(
            text = value,
            fontSize = EInkTheme.Typography.labelSmall,
            color = if (isChanged) EInkTheme.Colors.primary else EInkTheme.Colors.secondary,
            fontWeight = if (isChanged) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isChanged) {
            Text(
                text = "*",
                fontSize = EInkTheme.Typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }
    }
}

/**
 * Selectable resolution option.
 * E-Ink: Uses text symbol instead of Material Icon.
 */
@Composable
private fun ResolutionOption(
    title: String,
    description: String,
    iconResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Selection.background else Color.Transparent,
                RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .border(
                width = if (isSelected) EInkTheme.Global.borderWidth else 1.dp,
                color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(end = EInkTheme.Global.paddingMedium),
            tint = if (isSelected) EInkTheme.Selection.foreground else EInkTheme.Colors.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) EInkTheme.Selection.foreground else EInkTheme.Colors.primary
            )
            Text(
                text = description,
                fontSize = EInkTheme.Typography.labelSmall,
                color = if (isSelected) EInkTheme.Selection.foreground else EInkTheme.Colors.secondary
            )
        }
    }
    Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))
}

/**
 * List dialog for multiple unresolved conflicts.
 */
@Composable
fun ConflictListDialog(
    conflicts: List<ConflictInfo>,
    onSelectConflict: (ConflictInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
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
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.warning_40px),
                        contentDescription = "Warning",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = EInkTheme.Global.spacing),
                        tint = EInkTheme.ModernDialog.titleColor
                    )
                    Text(
                        text = "${conflicts.size} Sync Conflicts",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                Text(
                    text = "Select a conflict to resolve:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.secondary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    conflicts.forEach { conflict ->
                        ConflictListItem(
                            conflict = conflict,
                            onClick = { onSelectConflict(conflict) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                EinkSecondaryButton(
                    text = "Close",
                    onClick = onDismiss
                )
            }
        }
    }
}

/**
 * List item for a single conflict.
 * E-Ink: Uses text symbol instead of Material Icon.
 */
@Composable
private fun ConflictListItem(
    conflict: ConflictInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .noRippleClickable(onClick = onClick)
            .padding(EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.warning_40px),
            contentDescription = "Warning",
            modifier = Modifier
                .size(20.dp)
                .padding(end = EInkTheme.Global.paddingMedium),
            tint = EInkTheme.Colors.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conflict.localEvent.title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Fields: ${conflict.overlappingFields.joinToString(", ").ifEmpty { "multiple" }}",
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.secondary
            )
        }
    }
    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
}
