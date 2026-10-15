package com.boox.day.ui.dialogs

import androidx.compose.runtime.Composable
import com.boox.day.data.db.Notebook

@Composable
fun EmptyBookWarningHandler(
    emptyBook: Notebook, onDelete: (String) -> Unit, onDismiss: () -> Unit
) {
    val title = emptyBook.title
    val createdAt = emptyBook.createdAt
    ShowSimpleConfirmationDialog(
        title = "There is a book without pages!!!",
        message = "We suggest deleting book \"$title\", it was created at $createdAt. Do you want to do it?",
        onConfirm = {
            onDelete(emptyBook.id)
        },
        onCancel = { onDismiss() },
        confirmButtonText = "Delete Book"
    )
}
