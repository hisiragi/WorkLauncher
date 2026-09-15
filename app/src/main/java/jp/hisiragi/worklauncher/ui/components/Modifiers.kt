package jp.hisiragi.worklauncher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * [combinedClickable] is still experimental; wrapping it here keeps the opt-in
 * to one place instead of every call site.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

fun Modifier.alphaCompat(value: Float): Modifier = this.alpha(value)
