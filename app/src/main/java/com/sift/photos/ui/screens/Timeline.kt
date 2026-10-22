package com.sift.photos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sift.photos.data.DaySection
import com.sift.photos.data.PhotoItem
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.T
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class Zoom(val label: String) { Years("Years"), Months("Months"), Days("Days") }

data class MonthGroup(val year: Int, val month: Int, val label: String, val items: List<PhotoItem>, val firstSection: Int)

fun monthGroups(sections: List<DaySection>): List<MonthGroup> {
    val fmt = SimpleDateFormat("LLLL", Locale.getDefault())
    val cal = Calendar.getInstance()
    val out = LinkedHashMap<Int, MutableList<Pair<Int, DaySection>>>()
    sections.forEachIndexed { i, s ->
        cal.timeInMillis = s.items.first().dateMillis
        out.getOrPut(cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)) { mutableListOf() } += i to s
    }
    return out.map { (k, list) ->
        cal.set(k / 100, k % 100, 1)
        MonthGroup(k / 100, k % 100, fmt.format(Date(cal.timeInMillis)).replaceFirstChar { it.uppercase() }, list.flatMap { it.second.items }, list.first().first)
    }
}

/** Grid index of a day section: the Library header and zoom chips first, then a header plus cells per section. */
fun sectionGridIndex(sections: List<DaySection>, section: Int): Int =
    2 + sections.take(section).sumOf { 1 + it.items.size }

fun LazyGridScope.monthTiles(months: List<MonthGroup>, onOpen: (MonthGroup) -> Unit) {
    var lastYear = -1
    months.forEach { m ->
        if (m.year != lastYear) {
            lastYear = m.year
            item(key = "y${m.year}", span = { GridItemSpan(maxLineSpan) }) {
                T(m.year.toString(), Sift.type.headlineS, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp), color = Sift.colors.onSurface)
            }
        }
        item(key = "m${m.year}-${m.month}", span = { GridItemSpan(maxLineSpan / 2) }) {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp).clip(Shapes.l).clickable { onOpen(m) }
                    .semantics(mergeDescendants = true) {},
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(Shapes.l)) { PhotoView(m.items.first().ref, Modifier.fillMaxSize(), thumbSize = 240.dp) }
                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                    T(m.label, Sift.type.titleS, Modifier.weight(1f), color = Sift.colors.onSurface)
                    T("%,d".format(m.items.size), Sift.type.data, color = Sift.colors.onSurfaceVariant)
                }
            }
        }
    }
}

fun LazyGridScope.yearRows(months: List<MonthGroup>, onOpen: (MonthGroup) -> Unit) {
    months.groupBy { it.year }.forEach { (year, ms) ->
        item(key = "Y$year", span = { GridItemSpan(maxLineSpan) }) {
            val covers = ms.flatMap { it.items }.let { all -> listOf(0, all.size / 2, all.size - 1).distinct().map { all[it] } }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clip(Shapes.card).clickable { onOpen(ms.last()) }
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    covers.forEachIndexed { i, item ->
                        Box(Modifier.weight(if (i == 0) 2f else 1f).fillMaxHeight()) { PhotoView(item.ref, Modifier.fillMaxSize(), thumbSize = 240.dp) }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    T(year.toString(), Sift.type.headlineS, Modifier.weight(1f), color = Sift.colors.onSurface)
                    T("%,d items".format(ms.sumOf { it.items.size }), Sift.type.data, color = Sift.colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Fast scrubber: a right-edge rail with a 16 × 48 dp accent handle and an inverse date bubble
 * while dragging. Jumps by day section so it stays fast on 100k-photo libraries.
 */
@Composable
fun Scrubber(state: LazyGridState, sections: List<DaySection>, modifier: Modifier = Modifier) {
    if (sections.size < 4) return
    val c = Sift.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val starts = remember(sections) { sections.indices.map { sectionGridIndex(sections, it) } }
    val current by remember(starts) {
        derivedStateOf { starts.indexOfLast { it <= state.firstVisibleItemIndex }.coerceAtLeast(0) }
    }
    val fraction = if (dragging) drag else current.toFloat() / (sections.size - 1).coerceAtLeast(1)
    val section = (fraction * (sections.size - 1)).roundToInt().coerceIn(0, sections.lastIndex)
    val label = remember(section) {
        SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(sections[section].items.first().dateMillis))
    }
    BoxWithConstraints(
        modifier.fillMaxHeight().width(56.dp)
            .semantics { contentDescription = "Timeline scrubber"; stateDescription = label },
    ) {
        val track = with(density) { (maxHeight - 48.dp).toPx() }
        Box(
            Modifier.fillMaxSize().pointerInput(sections) {
                detectVerticalDragGestures(
                    onDragStart = { o -> dragging = true; drag = (o.y / track).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    drag = (change.position.y / track).coerceIn(0f, 1f)
                    val target = (drag * (sections.size - 1)).roundToInt().coerceIn(0, sections.lastIndex)
                    scope.launch { state.scrollToItem(starts[target]) }
                }
            },
        )
        val y = (fraction * track).roundToInt()
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y) }.padding(end = 4.dp)
                .size(16.dp, 48.dp).clip(Shapes.full).background(if (dragging) c.primary else c.outline),
        )
        if (dragging) {
            Box(
                Modifier.align(Alignment.TopEnd).offset { IntOffset(with(density) { -64.dp.roundToPx() }, y + with(density) { 8.dp.roundToPx() }) }
                    .clip(Shapes.m).background(c.inverseSurface).padding(horizontal = 12.dp, vertical = 6.dp),
            ) { T(label, Sift.type.labelL, color = c.inverseOnSurface, maxLines = 1) }
        }
    }
}
