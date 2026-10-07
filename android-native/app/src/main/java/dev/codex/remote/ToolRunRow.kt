package dev.codex.remote

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val TranscriptEase = CubicBezierEasing(.22f, .8f, .2f, 1f)

/** A bounded, opt-in audit drawer. Repeated commands stay in their original order, never deleted. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ToolRunRow(row: TranscriptRow, expanded: Boolean, onToggle: () -> Unit,
    loading: Set<String>, onLoad: (MessageItem) -> Unit, onCopyFull: suspend (MessageItem) -> MessageItem, stripe: Int = 0,
    activeToolIds: Set<String> = row.items.filter { it.status == "inProgress" }.map { it.id }.toSet()) {
    val motion = rememberChatMotion()
    val palette = LocalAppPalette.current
    val appearance = LocalChatAppearance.current
    val muted = zebraSecondary(appearance.zebra, palette.isNight, palette.muted)
    val surface = zebraSurface(appearance.zebra, stripe, palette.isNight).compositeOver(palette.background)
    var openTool by rememberSaveable(row.key) { mutableStateOf<String?>(null) }
    val opened = row.items.find { it.id == openTool }
    val codeRows = remember(opened) { opened?.let { toolCodeRows(it) }.orEmpty() }
    val detailHeight = if (opened == null) 0.dp else toolCodeHeight(codeRows) + (if (opened.truncated) 48.dp else 0.dp)
    val targetHeight = (toolHeaderHeight() * row.items.size + detailHeight).coerceAtMost(340.dp)
    val listHeight by animateDpAsState(targetHeight, tween(if (motion) 360 else 0, easing = TranscriptEase), label = "tool-drawer-height")
    val failed = row.items.any { it.status == "failed" || it.exitCode?.let { code -> code != 0 } == true }
    Column(Modifier.fillMaxWidth().background(surface).testTag(row.key)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle-${row.key}")
            .semantics { stateDescription = if (expanded) "已展开执行记录" else "已收起执行记录" }
            .clickable(role = Role.Button, onClickLabel = if (expanded) "收起执行记录" else "查看执行记录", onClick = onToggle)
            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ToolChevron(expanded, "chevron-${row.key}")
            Text(toolRunSummary(row.items), Modifier.weight(1f).testTag("status-${row.key}")
                .processingLight(row.items.any { it.id in activeToolIds }), fontSize = 12.sp,
                color = if (failed) palette.errorInk else muted)
            row.items.lastOrNull()?.let { ContentTime(it, row.items.any { item -> item.id in activeToolIds }) }
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(tween(if (motion) 380 else 0, easing = TranscriptEase), expandFrom = Alignment.Top) +
                fadeIn(tween(if (motion) 240 else 0, delayMillis = if (motion) 40 else 0)),
            exit = shrinkVertically(tween(if (motion) 300 else 0, easing = TranscriptEase), shrinkTowards = Alignment.Top) +
                fadeOut(tween(if (motion) 160 else 0))) {
            Column(Modifier.fillMaxWidth().clipToBounds().testTag("details-${row.key}")) {
                // Short records fit their content. Long collections remain bounded; each command's
                // heading is sticky so its collapse/copy controls cannot disappear behind output.
                LazyColumn(Modifier.fillMaxWidth().height(listHeight)
                    .testTag("list-${row.key}")) {
                    row.items.forEachIndexed { index, command ->
                        stickyHeader(key = "heading-${command.id}") {
                            Box(Modifier.fillMaxWidth().background(zebraSurface(appearance.zebra, index, palette.isNight).compositeOver(palette.background))) {
                                ToolHeader(command, openTool == command.id, { openTool = if (openTool == command.id) null else command.id },
                                    command.id in loading, onCopyFull, "${index + 1}/${row.items.size}", processing = command.id in activeToolIds)
                            }
                        }
                        item(key = "body-${command.id}") {
                            ToolDetails(command, openTool == command.id, command.id in loading) { onLoad(command) }
                        }
                    }
                }
                HorizontalDivider(color = palette.divider.copy(alpha = .45f), thickness = .5.dp)
            }
        }
    }
}

/** A square vector viewport avoids clipping the old narrow text glyph when it rotates down. */
@Composable
internal fun ToolChevron(expanded: Boolean, tag: String) {
    val motion = rememberChatMotion()
    val angle by animateFloatAsState(if (expanded) 90f else 0f,
        tween(if (motion) 320 else 0, easing = TranscriptEase), label = "tool-chevron")
    val palette = LocalAppPalette.current
    val color = zebraSecondary(LocalChatAppearance.current.zebra, palette.isNight, palette.muted)
    Canvas(Modifier.size(24.dp).testTag(tag).graphicsLayer { rotationZ = angle }) {
        val path = Path().apply {
            moveTo(size.width * .39f, size.height * .3f)
            lineTo(size.width * .61f, size.height * .5f)
            lineTo(size.width * .39f, size.height * .7f)
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}
