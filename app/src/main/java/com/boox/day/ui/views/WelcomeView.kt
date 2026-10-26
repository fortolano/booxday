package com.boox.day.ui.views

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.boox.day.PACKAGE_NAME
import com.boox.day.R
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.editor.utils.setRecommendedMode
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.components.ScrollMoreBar
import com.boox.day.ui.theme.EInkTheme

/**
 * Welcome View - First-run screen for new users.
 *
 * E-Ink optimized:
 * - Uses EInkTheme for all colors and typography
 * - No animations or ripple effects
 * - High contrast black/white styling
 * - Clear visual hierarchy
 */
@Composable
fun WelcomeView(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Auto-set optimal refresh mode for e-ink display
    LaunchedEffect(Unit) {
        setRecommendedMode()
    }

    // Oct 2026: "Get Started" stays below the scrolling part, with the "Scroll for more" bar, so it is
    // always on screen (on the Go 6 it was below the edge, with no hint)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EInkTheme.Colors.background)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // App Icon (actual BooxDay logo)
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground_vector),
                    contentDescription = "BooxDay",
                    modifier = Modifier.size(80.dp),
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Welcome Title
                Text(
                    text = stringResource(R.string.welcome_view_title),
                    fontSize = EInkTheme.Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tagline
                Text(
                    text = stringResource(R.string.welcome_view_subtitle),
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = EInkTheme.Typography.bodyLarge * 1.4f
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Features Grid (2x2)
                FeaturesGrid()

                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights - AI + Boox Notes side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiFeaturesHighlight(modifier = Modifier.weight(1f))
                    BooxNotesHighlight(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // More features, and where Help is
                AlsoInBooxDayCard()

                Spacer(modifier = Modifier.height(24.dp))

                // Disclaimer
                Text(
                    text = "An independent app for Boox devices",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Made for Boox lovers, by Boox lovers",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
            ScrollMoreBar(scrollState)
        }

        // Get Started Button
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    EInkTheme.Colors.primary,
                    RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                )
                .noRippleClickable {
                    KvProxy(context).setAppSettings(
                        GlobalAppSettings.current.copy(showWelcome = false)
                    )
                    // Navigate to main and remove welcome from backstack
                    // NOTE: Do NOT call refreshScreen() here - GC refresh captures the current
                    // screen state (WelcomeView) and redraws it during the 1-2s refresh cycle,
                    // causing a "flash" effect while MainScreen tries to render underneath.
                    navController.navigate("main") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.welcome_view_continue),
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.white
            )
        }
    }
}

@Composable
private fun FeaturesGrid() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureChip(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.welcome_view_feature_calendar_title),
                modifier = Modifier.weight(1f)
            )
            FeatureChip(
                icon = Icons.Outlined.Draw,
                title = stringResource(R.string.welcome_view_feature_stylus_title),
                modifier = Modifier.weight(1f)
            )
        }
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureChip(
                icon = Icons.Outlined.Mic,
                title = stringResource(R.string.welcome_view_feature_voice_title),
                modifier = Modifier.weight(1f)
            )
            FeatureChip(
                icon = Icons.Outlined.TaskAlt,
                title = stringResource(R.string.welcome_view_feature_tabs_title),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeatureChip(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                EInkTheme.Colors.offWhite,
                RoundedCornerShape(12.dp)
            )
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.lightGray,
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = EInkTheme.Colors.background,
                        shape = CircleShape
                    )
                    .border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.lightGray,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 2,
                lineHeight = EInkTheme.Typography.bodyMedium * 1.2f
            )
        }
    }
}

/**
 * AI features highlight card (Oct 2026: was "AI Chief of Staff").
 * Compact version for side-by-side layout with Boox Notes card.
 */
@Composable
private fun AiFeaturesHighlight(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                EInkTheme.Colors.offWhite,
                RoundedCornerShape(16.dp)
            )
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.primary,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        // Header row with icon and title
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        EInkTheme.Colors.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = EInkTheme.Colors.white,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "AI features",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Feature bullets (condensed to 2)
        HighlightBullet("Smart Suggestions and Optimize My Day, from your calendar and notes")
        Spacer(modifier = Modifier.height(6.dp))
        HighlightBullet("Run on your own OpenRouter account, added in AI Settings")
    }
}

/**
 * Boox Notes Integration highlight card - shows Smart Link and search.
 * Matches the AI features card styling for side-by-side layout.
 */
@Composable
private fun BooxNotesHighlight(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                EInkTheme.Colors.offWhite,
                RoundedCornerShape(16.dp)
            )
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.primary,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        // Header row with icon and title
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        EInkTheme.Colors.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = EInkTheme.Colors.white,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Boox Notes",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Feature bullets
        HighlightBullet("Smart Link connects your notes to calendar events")
        Spacer(modifier = Modifier.height(6.dp))
        HighlightBullet("Search finds the words in your handwritten notes")
    }
}

@Composable
private fun HighlightBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = EInkTheme.Typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.labelMedium,
            color = EInkTheme.Colors.darkGray,
            lineHeight = EInkTheme.Typography.labelMedium * 1.3f
        )
    }
}

/**
 * More features, and where Help is (Oct 2026: this card listed permissions the app would ask for,
 * but nothing asks at this point).
 * E-ink optimized with clean icons and high contrast.
 */
@Composable
private fun AlsoInBooxDayCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                EInkTheme.Colors.offWhite,
                RoundedCornerShape(12.dp)
            )
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.lightGray,
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Also in BooxDay",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Feature items
        FeatureItem(
            icon = Icons.Outlined.Draw,
            label = "Full screen",
            description = "more room to write"
        )
        Spacer(modifier = Modifier.height(6.dp))
        FeatureItem(
            icon = Icons.Outlined.CalendarMonth,
            label = "Sleep screen",
            description = "your day while the tablet sleeps"
        )
        Spacer(modifier = Modifier.height(6.dp))
        FeatureItem(
            icon = Icons.Outlined.Folder,
            label = "Daily notes",
            description = "each day as a Markdown file"
        )
        Spacer(modifier = Modifier.height(6.dp))
        FeatureItem(
            icon = Icons.Outlined.Sync,
            label = "Updates",
            description = "install from inside the app"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Footer
        Text(
            text = "Settings \u2192 Help explains every feature.",
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    label: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    EInkTheme.Colors.background,
                    CircleShape
                )
                .border(
                    EInkTheme.Global.borderWidth,
                    EInkTheme.Colors.lightGray,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = EInkTheme.Typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.darkGray
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "\u2014 $description",
            fontSize = EInkTheme.Typography.labelMedium,
            color = EInkTheme.Colors.gray
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Returns true if the app has "full file access" for your current storage model:
 * - < Android 11: WRITE_EXTERNAL_STORAGE is granted
 * - >= Android 11: MANAGE_EXTERNAL_STORAGE ("All files access") is granted
 */
fun hasFilePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestPermissions(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }
    } else if (!Environment.isExternalStorageManager()) {
        requestManageAllFilesPermission(context)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun requestManageAllFilesPermission(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.data = Uri.fromParts("package", PACKAGE_NAME, null)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}
