package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * CalDAV accounts in Calendar Accounts (plan stage 3, step 3.6, Nov 2026; owner decisions 58-59,
 * pictures approved 2 Nov 2026): the "iCloud & more" tab's add buttons, the sign-in form, the Trust
 * This Server question and the CalDAV mark. Stateless content; CalDavSignInPopup draws the popup
 * and does the signing in.
 */

/** The words on the third tab of Calendar Accounts (owner decisions 58 and 3.6's 1A: words only). */
internal const val CALDAV_TAB_LABEL = "iCloud & more"

/**
 * The CalDAV mark on accounts, the events filter and events (owner's choice 2A): a calendar with a
 * small globe, a calendar on a server. Never a service's own logo (Apple doesn't allow its logo in
 * other apps, and one mark covers every server). [background] fills behind the globe.
 */
@Composable
fun CalDavMark(size: Dp, tint: Color = EInkTheme.Colors.primary, background: Color = Color.White) {
    Box(Modifier.size(size)) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        Box(
            Modifier
                .size(size * 0.55f)
                .align(Alignment.BottomEnd)
                .offset(x = size * 0.08f, y = size * 0.08f)
                .background(background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
        }
    }
}

/** The third tab's face. */
@Composable
internal fun CalDavTabLabel(selected: Boolean) {
    Text(
        text = CALDAV_TAB_LABEL,
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary,
        maxLines = 1
    )
}

// ── Tab content ──

/** One add button on the tab (owner decision 59): what it adds and a line under it. */
internal data class CalDavChoice(val type: AccountType, val label: String, val detail: String)

internal val calDavChoices = listOf(
    CalDavChoice(AccountType.CALDAV_ICLOUD, "Add iCloud Account", "Apple calendars"),
    CalDavChoice(AccountType.CALDAV_FASTMAIL, "Add Fastmail Account", "Fastmail calendars"),
    CalDavChoice(AccountType.CALDAV_NEXTCLOUD, "Add Nextcloud Account", "Your Nextcloud server"),
    CalDavChoice(AccountType.CALDAV_OTHER, "Add Other Account", "Any CalDAV server (https)")
)

internal const val CALDAV_EMPTY_MESSAGE = "No iCloud or other calendars connected.\nAdd an account to sync its calendars."

@Composable
internal fun CalDavEmptyMessage() {
    Box(Modifier.fillMaxWidth().padding(EInkTheme.Global.padding), contentAlignment = Alignment.Center) {
        Text(CALDAV_EMPTY_MESSAGE, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.gray, textAlign = TextAlign.Center)
    }
}

/** Two rows of two, styled like the Google and Outlook add buttons. Always on: any number of accounts. */
@Composable
internal fun CalDavAddButtons(enabled: Boolean = true, onAdd: (AccountType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        calDavChoices.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                pair.forEach { choice ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(
                            onClick = { onAdd(choice.type) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().height(EInkTheme.Global.touchTarget),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EInkTheme.Colors.primary,
                                disabledContentColor = EInkTheme.Colors.gray
                            ),
                            border = BorderStroke(EInkTheme.Global.borderWidth, if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray),
                            shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(EInkTheme.Global.spacingSmall))
                            Text(choice.label, fontSize = EInkTheme.Typography.bodySmall, maxLines = 1)
                        }
                        Text(choice.detail, fontSize = 11.sp, color = EInkTheme.Colors.gray, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ── Sign-in form ──

/** The form's words for each kind of account (help steps checked against Apple's, Fastmail's and Nextcloud's help, Nov 2026). */
internal data class CalDavFormText(
    val title: String,
    val askAddress: Boolean,
    val addressHint: String,
    val nameLabel: String,
    val nameHint: String,
    val passwordLabel: String,
    val howTitle: String,
    val steps: List<String>
)

internal fun calDavFormText(type: AccountType): CalDavFormText = when (type) {
    AccountType.CALDAV_ICLOUD -> CalDavFormText(
        title = "Add iCloud Account",
        askAddress = false, addressHint = "",
        nameLabel = "Apple Account email", nameHint = "name@icloud.com",
        passwordLabel = "App-specific password",
        howTitle = "iCloud needs an app-specific password, not your usual Apple password:",
        steps = listOf(
            "On a phone or computer, sign in at account.apple.com.",
            "Sign-In and Security → App-Specific Passwords.",
            "Make one named BooxDay and type it here."
        )
    )
    AccountType.CALDAV_FASTMAIL -> CalDavFormText(
        title = "Add Fastmail Account",
        askAddress = false, addressHint = "",
        nameLabel = "Fastmail email", nameHint = "name@fastmail.com",
        passwordLabel = "App password",
        howTitle = "Fastmail needs an app password, not your usual password:",
        steps = listOf(
            "Sign in at fastmail.com.",
            "Settings → Privacy & Security → App passwords.",
            "Make one with access to Calendars (CalDAV) and type it here."
        )
    )
    AccountType.CALDAV_NEXTCLOUD -> CalDavFormText(
        title = "Add Nextcloud Account",
        askAddress = true, addressHint = "cloud.example.com",
        nameLabel = "User name", nameHint = "",
        passwordLabel = "App password",
        howTitle = "Use an app password, not your usual password:",
        steps = listOf(
            "In Nextcloud: your picture → Settings → Security.",
            "Devices & sessions: type BooxDay, then Create new app password.",
            "Type it here."
        )
    )
    else -> CalDavFormText(
        title = "Add a Calendar Server",
        askAddress = true, addressHint = "dav.example.com",
        nameLabel = "User name", nameHint = "",
        passwordLabel = "Password",
        howTitle = "Any server with CalDAV over https, for example Radicale, Baïkal, Synology Calendar or mailbox.org.",
        steps = emptyList()
    )
}

internal const val CALDAV_PASSWORD_NOTE = "Your password stays on this tablet, encrypted, and is sent only to this server."

internal data class CalDavSignInState(
    val type: AccountType,
    val address: String = "",
    val name: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val connecting: Boolean = false,
    /** What went wrong, in plain words (bold above the buttons). */
    val message: String? = null,
    /** Signing in again to an account whose password the server refused: the account can't change. */
    val again: Boolean = false
) {
    val ready: Boolean
        get() = password.isNotBlank() && name.isNotBlank() && (!calDavFormText(type).askAddress || address.isNotBlank())

    companion object {
        /** The form for signing in again (Re-auth): the account's server and name, a new password. */
        fun again(account: ConnectedAccount) = CalDavSignInState(
            type = account.accountType,
            address = account.serverUrl,
            name = account.email,
            again = true
        )
    }
}

/** The sign-in form: fields, how to get the password, the privacy note, Cancel / Connect. */
@Composable
internal fun CalDavSignInContent(
    state: CalDavSignInState,
    onAddress: (String) -> Unit = {},
    onName: (String) -> Unit = {},
    onPassword: (String) -> Unit = {},
    onShowPassword: () -> Unit = {},
    onPaste: () -> Unit = {},
    onCancel: () -> Unit = {},
    onConnect: () -> Unit = {}
) {
    val text = calDavFormText(state.type)
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Text(
            text = if (state.again) "Sign In to ${state.type.displayName} Again" else text.title,
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
        if (text.askAddress) {
            FormField("Server address", state.address, text.addressHint, onAddress, enabled = !state.again && !state.connecting)
        }
        FormField(
            text.nameLabel, state.name, text.nameHint, onName,
            enabled = !state.again && !state.connecting, keyboard = KeyboardType.Email
        )
        FormField(
            text.passwordLabel, state.password, "", onPassword,
            enabled = !state.connecting, password = !state.showPassword, keyboard = KeyboardType.Password
        ) {
            SmallSquareButton(Icons.Default.ContentPaste, "Paste", onPaste)
            SmallSquareButton(
                if (state.showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                if (state.showPassword) "Hide password" else "Show password",
                onShowPassword
            )
        }
        HowToBox(text)
        Text(CALDAV_PASSWORD_NOTE, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
        state.message?.let {
            Text(it, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
        }
        Row(Modifier.fillMaxWidth().padding(top = EInkTheme.Global.spacingSmall)) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(0.4f))
            Spacer(Modifier.weight(0.2f))
            EinkPrimaryButton(
                text = if (state.connecting) "Connecting…" else "Connect",
                enabled = state.ready && !state.connecting,
                onClick = onConnect,
                modifier = Modifier.weight(0.4f)
            )
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Uri,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = EInkTheme.TextField.labelFontSize, fontWeight = FontWeight.Medium, color = EInkTheme.TextField.labelColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .border(1.dp, if (enabled) EInkTheme.Colors.darkGray else EInkTheme.Colors.lightGray)
                    .background(EInkTheme.Colors.white)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray
                    ),
                    singleLine = true,
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
                    decorationBox = { inner ->
                        if (value.isEmpty() && hint.isNotEmpty()) {
                            Text(hint, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.gray)
                        }
                        inner()
                    }
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SmallSquareButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .border(1.dp, EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = EInkTheme.Colors.darkGray, modifier = Modifier.size(20.dp))
    }
}

/** How to get the password: a light box with numbered steps. */
@Composable
private fun HowToBox(text: CalDavFormText) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text.howTitle, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.primary)
        text.steps.forEachIndexed { i, step ->
            Row {
                Text("${i + 1}. ", fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
                Text(step, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
            }
        }
    }
}

// ── Trust This Server (owner decision 5) ──

/**
 * A server whose certificate Android can't check (a home server's own). Plain words first; the
 * fingerprint, which only matters to someone comparing it with the server's, is folded away
 * (owner, 2 Nov 2026: hide the technical part). [changed]: it was trusted before and the
 * certificate is now another one.
 */
@Composable
internal fun TrustServerContent(
    host: String,
    issuedTo: String,
    sha256: String,
    changed: Boolean = false,
    showDetails: Boolean = false,
    onToggleDetails: () -> Unit = {},
    onCancel: () -> Unit = {},
    onTrust: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Text(
            text = if (changed) "Server Certificate Changed" else "Trust This Server?",
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
        Text(
            text = if (changed) {
                "$host now shows a different security certificate from the one you trusted. If you didn't change " +
                    "it on your server, don't trust it."
            } else {
                "$host uses its own security certificate, which Android can't check. That's normal for a home " +
                    "server. Trust it only if it's your own server."
            },
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
        Row(
            modifier = Modifier.heightIn(min = EInkTheme.Global.touchTarget).noRippleClickable { onToggleDetails() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (showDetails) "Hide certificate details" else "Show certificate details",
                fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray
            )
            Icon(
                if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = EInkTheme.Colors.darkGray, modifier = Modifier.size(18.dp)
            )
        }
        if (showDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .padding(EInkTheme.Global.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Issued to: $issuedTo", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
                Text("Fingerprint (SHA-256), to compare with your server's:", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
                fingerprintLines(sha256).forEach {
                    Text(it, fontSize = EInkTheme.Typography.bodySmall, fontFamily = FontFamily.Monospace, color = EInkTheme.Colors.primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = EInkTheme.Global.spacingSmall)) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(0.4f))
            Spacer(Modifier.weight(0.15f))
            EinkPrimaryButton(text = "Trust This Server", onClick = onTrust, modifier = Modifier.weight(0.45f))
        }
    }
}

/** "AB:CD:…" (32 pairs) as two lines of 16. */
internal fun fingerprintLines(sha256: String): List<String> =
    sha256.split(':').chunked(16).map { it.joinToString(":") }
