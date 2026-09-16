package jp.hisiragi.worklauncher.ui.widgets

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.WidgetHostController
import jp.hisiragi.worklauncher.data.db.HomeWidgetEntity

/** One swipeable slot: the widgets sharing a stack id, plus its page dots. */
@Composable
fun WidgetStack(
    widgets: List<HomeWidgetEntity>,
    controller: WidgetHostController,
    editing: Boolean,
    onAddToStack: () -> Unit,
    onRemove: (HomeWidgetEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (widgets.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { widgets.size })
    val height = widgets.first().heightDp

    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp),
                pageSpacing = 8.dp,
            ) { page ->
                WidgetFrame(
                    widget = widgets[page],
                    controller = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height.dp),
                )
            }

            if (editing) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    IconButton(onClick = onAddToStack, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.widget_add_to_stack),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onRemove(widgets[pagerState.currentPage]) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.widget_remove),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        if (widgets.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(widgets.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetFrame(
    widget: HomeWidgetEntity,
    controller: WidgetHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val info = controller.providerInfo(widget.appWidgetId)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    ) {
        if (info == null) {
            // The provider was uninstalled; the row is cleaned up on next edit.
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.widget_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AndroidView(
                factory = { viewContext ->
                    controller.createView(viewContext, widget.appWidgetId)?.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    } ?: android.widget.FrameLayout(viewContext)
                },
                update = { view ->
                    if (view is android.appwidget.AppWidgetHostView) {
                        val widthDp = (context.resources.displayMetrics.widthPixels /
                            context.resources.displayMetrics.density).toInt() - 32
                        controller.resize(view, widthDp, widget.heightDp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
