package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.AppRepository
import com.boox.day.data.db.newPage
import com.boox.day.data.deletePage
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme


@Composable
fun PageMenu(
    notebookId: String? = null,
    pageId: String,
    index: Int? = null,
    canDelete: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val appRepository = AppRepository(context)
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = { onClose() },
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier
                .widthIn(min = EInkTheme.PopupMenu.width)
                .background(EInkTheme.PopupMenu.background)
                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.PopupMenu.padding)
        ) {
            if (notebookId != null && index != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.MenuItem.height)
                        .noRippleClickable {
                            appRepository.bookRepository.changePageIndex(
                                notebookId,
                                pageId,
                                index - 1
                            )
                        }
                        .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Move Left", color = EInkTheme.MenuItem.textColor, fontSize = EInkTheme.MenuItem.fontSize)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.MenuItem.height)
                        .noRippleClickable {
                            appRepository.bookRepository.changePageIndex(
                                notebookId,
                                pageId,
                                index + 1
                            )
                        }
                        .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Move Right", color = EInkTheme.MenuItem.textColor, fontSize = EInkTheme.MenuItem.fontSize)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.MenuItem.height)
                        .noRippleClickable {
                            val book = appRepository.bookRepository.getById(notebookId)
                                ?: return@noRippleClickable
                            val page = book.newPage()
                            appRepository.pageRepository.create(page)
                            appRepository.bookRepository.addPage(notebookId, page.id, index + 1)
                        }
                        .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Insert after", color = EInkTheme.MenuItem.textColor, fontSize = EInkTheme.MenuItem.fontSize)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.MenuItem.height)
                    .noRippleClickable {
                        appRepository.duplicatePage(pageId)
                    }
                    .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Duplicate", color = EInkTheme.MenuItem.textColor, fontSize = EInkTheme.MenuItem.fontSize)
            }
            if (canDelete) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.MenuItem.height)
                        .noRippleClickable {
                            deletePage(context, pageId)
                        }
                        .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Delete", color = EInkTheme.MenuItem.textColor, fontSize = EInkTheme.MenuItem.fontSize)
                }
            }
        }
    }
}

