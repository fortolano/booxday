package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.TAG
import com.boox.day.data.db.FolderRepository
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import android.util.Log


@ExperimentalComposeUiApi
@Composable
fun FolderConfigDialog(folderId: String, onClose: () -> Unit) {
    val folderRepository = FolderRepository(LocalContext.current)
    val folder = folderRepository.get(folderId)

    var folderTitle by remember {
        mutableStateOf(folder.title)
    }


    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            Log.i(TAG, "Closing Directory Dialog - upstream")
            onClose()
        },
        properties = PopupProperties(focusable = true)
    ) {
        val focusManager = LocalFocusManager.current

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            ) {
            Column(
                Modifier.padding(20.dp, 10.dp)
            ) {
                Text(text = "Folder Setting", fontWeight = FontWeight.Bold, fontSize = EInkTheme.PopupOverlay.titleFontSize, color = EInkTheme.PopupOverlay.titleColor)
            }
            Box(
                Modifier
                    .height(EInkTheme.Divider.thickness)
                    .fillMaxWidth()
                    .background(EInkTheme.Divider.color)
            )

            Column(
                Modifier.padding(20.dp, 10.dp)
            ) {

                Row {
                    Text(
                        text = "Folder Title",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.Typography.bodyLarge,
                        color = EInkTheme.Colors.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = folderTitle,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Light,
                            fontSize = EInkTheme.Typography.bodyLarge,
                            color = EInkTheme.Colors.primary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        onValueChange = { folderTitle = it },
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .background(EInkTheme.Colors.offWhite)
                            .padding(10.dp, 0.dp)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    val updatedFolder = folder.copy(title = folderTitle)
                                    folderRepository.update(updatedFolder)
                                }
                            }


                    )

                }
            }

            Box(
                Modifier
                    .padding(20.dp, 0.dp)
                    .height(EInkTheme.Divider.subtleThickness)
                    .fillMaxWidth()
                    .background(EInkTheme.Divider.color)
            )

            Column(
                Modifier.padding(20.dp, 10.dp)
            ) {
                Text(
                    text = "Delete Folder",
                    textAlign = TextAlign.Center,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.noRippleClickable {
                        folderRepository.delete(folderId)
                        onClose()
                    })
            }
            }
        }
    }
}