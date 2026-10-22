package com.sift.photos.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sift.photos.data.DocParser
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.SampleData
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.OriginBadge
import com.sift.photos.ui.components.PhotoCell
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Web Mercator in "world pixels" at zoom 0 (a 256 px square). */
private object Mercator {
    fun x(lon: Double) = (lon + 180.0) / 360.0 * 256.0
    fun y(lat: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0 * 256.0
    }
    fun lon(x: Double) = x / 256.0 * 360.0 - 180.0
    fun lat(y: Double) = Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2 * y / 256.0))))
}

/**
 * OpenStreetMap tiles: the only thing Sift fetches from the network. Cached on disk for 30 days,
 * with a descriptive User-Agent as the OSM tile usage policy asks. No photo data is ever sent.
 */
class TileCache(private val context: Context) {
    private val memory = LruCache<String, ImageBitmap>(160)
    private val dir = File(context.cacheDir, "tiles")

    suspend fun get(z: Int, x: Int, y: Int, network: Boolean): ImageBitmap? {
        val key = "$z/$x/$y"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(dir, "$key.png")
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 30L * 86_400_000
            if (!fresh && network) runCatching {
                val conn = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Sift/0.1 (Android photo library; com.sift.photos)")
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                if (conn.responseCode == 200) {
                    file.parentFile?.mkdirs()
                    conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                conn.disconnect()
            }
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { memory.put(key, it) }
        }
    }
}

data class Located(val item: PhotoItem, val lat: Double, val lon: Double)

/** Sample-library coordinates for the designed Lisbon places. */
private val samplePlaces = mapOf(
    "Lisbon" to (38.7105 to -9.1393), "Sintra" to (38.7976 to -9.3904), "Cascais" to (38.6979 to -9.4215),
    "Cais do Sodré, Lisbon" to (38.7066 to -9.1363),
)

fun SiftViewModel.locatedItems(): List<Located> = if (sample) {
    SampleData.allItems.mapNotNull { i -> samplePlaces[i.place]?.let { (la, lo) -> Located(i, la + (i.key.hashCode() % 7) * 0.0012, lo + (i.key.hashCode() % 5) * 0.0015) } } +
        SampleData.albums.flatMap { it.items }.mapNotNull { i -> samplePlaces[i.place]?.let { Located(i, it.first, it.second) } }
} else deviceItems.mapNotNull { i -> entry(i)?.let { e -> if (e.lat != null && e.lon != null) Located(i, e.lat, e.lon) else null } }

private val quietTiles = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) })
private val darkTiles = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(
        -0.85f, 0f, 0f, 0f, 235f,
        0f, -0.85f, 0f, 0f, 235f,
        0f, 0f, -0.85f, 0f, 235f,
        0f, 0f, 0f, 1f, 0f,
    )).apply { timesAssign(ColorMatrix().apply { setToSaturation(0.1f) }) }
)

@Composable
fun MapScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val tiles = remember { TileCache(context.applicationContext) }
    val network = vm.settings.mapTiles
    var range by remember { mutableIntStateOf(0) }
    val all = remember(vm.sample, vm.index.entries.size, vm.deviceItems.size) { vm.locatedItems() }
    val now = System.currentTimeMillis()
    val points = all.filter {
        when (range) {
            1 -> it.item.dateMillis > now - 365L * 86_400_000
            2 -> it.item.dateMillis > now - 30L * 86_400_000
            else -> true
        }
    }
    // Camera: world-pixel center at zoom 0 and a fractional zoom.
    val initial = remember(all.isEmpty()) { fit(all) }
    var cx by remember { mutableDoubleStateOf(initial.first) }
    var cy by remember { mutableDoubleStateOf(initial.second) }
    var zoom by remember { mutableFloatStateOf(initial.third) }
    val loaded = remember { mutableStateMapOf<String, ImageBitmap>() }
    val loading = remember { mutableStateMapOf<String, Boolean>() }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF1E1E1D) else Color(0xFFECEAE5))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            val z = floor(zoom).toInt().coerceIn(1, 18)
            val scale = 2.0.pow(zoom.toDouble())
            val tileScale = 2.0.pow((zoom - z).toDouble())
            val n = 1 shl z
            // Visible tiles at integer zoom z.
            val left = (cx * scale - w / 2) / (256 * tileScale) * 1
            val top = (cy * scale - h / 2) / (256 * tileScale)
            val tx0 = floor(left).toInt(); val ty0 = floor(top).toInt()
            val tx1 = floor(left + w / (256 * tileScale)).toInt(); val ty1 = floor(top + h / (256 * tileScale)).toInt()
            val needed = buildList { for (tx in tx0..tx1) for (ty in ty0..ty1) if (ty in 0 until n) add(Triple(z, ((tx % n) + n) % n, ty) to (tx to ty)) }
            LaunchedEffect(needed.map { it.first }, network) {
                needed.forEach { (t, _) ->
                    val key = "${t.first}/${t.second}/${t.third}"
                    if (key !in loaded && loading[key] != true) {
                        loading[key] = true
                        scope.launch {
                            val bmp = tiles.get(t.first, t.second, t.third, network)
                            if (bmp != null) loaded[key] = bmp else failed = network
                            loading[key] = false
                        }
                    }
                }
            }
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val s0 = 2.0.pow(zoom.toDouble())
                            // Keep the point under the fingers fixed while zooming.
                            val wx = cx + (centroid.x - size.width / 2) / s0
                            val wy = cy + (centroid.y - size.height / 2) / s0
                            zoom = (zoom + log2(gestureZoom)).coerceIn(2f, 18f)
                            val s1 = 2.0.pow(zoom.toDouble())
                            cx = wx - (centroid.x - size.width / 2) / s1 - pan.x / s1
                            cy = wy - (centroid.y - size.height / 2) / s1 - pan.y / s1
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { p ->
                            val s0 = 2.0.pow(zoom.toDouble())
                            cx += (p.x - size.width / 2) / s0 / 2; cy += (p.y - size.height / 2) / s0 / 2
                            zoom = (zoom + 1).coerceAtMost(18f)
                        })
                    }
                    .semantics { contentDescription = "Map of your photos. Pinch to zoom, drag to move." },
            ) {
                val size = (256 * tileScale).toFloat()
                needed.forEach { (t, pos) ->
                    val img = loaded["${t.first}/${t.second}/${t.third}"] ?: return@forEach
                    val dx = (pos.first * size - (cx * scale - w / 2)).toFloat()
                    val dy = (pos.second * size - (cy * scale - h / 2)).toFloat()
                    drawImage(
                        img, dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize(size.roundToInt() + 1, size.roundToInt() + 1),
                        colorFilter = if (c.isDark) darkTiles else quietTiles,
                    )
                }
            }
            // Photo clusters.
            val pinPx = with(density) { 56.dp.toPx() }
            val screen = { l: Located -> ((Mercator.x(l.lon) - cx) * scale + w / 2).toFloat() to ((Mercator.y(l.lat) - cy) * scale + h / 2).toFloat() }
            val clusters = com.sift.photos.data.Analysis.cluster(points.filter { p -> screen(p).let { it.first in -pinPx..(w + pinPx) && it.second in -pinPx..(h + pinPx) } }, pinPx, screen)
            clusters.forEach { group ->
                val (sx, sy) = screen(group.first())
                val cover = group.maxBy { it.item.dateMillis }
                Box(
                    Modifier
                        .offset { IntOffset((sx - pinPx / 2).roundToInt(), (sy - pinPx / 2).roundToInt()) }
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(3.dp, Color.White, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClickLabel = "Open ${group.size} photos") {
                            val items = group.map { it.item }.sortedByDescending { it.dateMillis }
                            vm.push(Screen.Place(placeTitle(items), items, group.map { it.lat }.average(), group.map { it.lon }.average()))
                        }
                        .semantics { contentDescription = "${group.size} photos" },
                ) {
                    PhotoView(cover.item.ref, Modifier.fillMaxSize(), thumbSize = 56.dp)
                    if (group.size > 1) Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).clip(Shapes.full).background(c.inverseSurface).padding(horizontal = 6.dp, vertical = 1.dp),
                    ) { T(if (group.size > 999) "999+" else group.size.toString(), Sift.type.dataMedium, color = c.inverseOnSurface) }
                }
            }
        }

        // Top: search-style bar with origin badges.
        Row(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().clip(Shapes.full).background(c.containerHigh).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn("arrow_back", "Back", { vm.pop() }, tint = c.onSurface)
            T("Places", Sift.type.titleM, Modifier.weight(1f), color = c.onSurface)
            if (network) OriginBadge(cloud = true, text = "Cloud · map tiles only") else OriginBadge(text = "Offline map")
        }

        // Peek sheet.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(Shapes.sheet).background(c.modal)
                .then(if (c.hairline != null) Modifier.border(1.dp, c.hairline, Shapes.sheet) else Modifier)
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All time", "Last 12 months", "Last 30 days").forEachIndexed { i, l -> SiftChip(l, range == i, { range = i }) }
            }
            T(
                if (points.isEmpty()) "No photos with a location" + if (range != 0) " in this range" else ""
                else "%,d photos · %,d places".format(points.size, com.sift.photos.data.Analysis.cluster(points, 0.01f) { it.lat.toFloat() to it.lon.toFloat() }.size),
                Sift.type.titleM, color = c.onSurface,
            )
            when {
                !vm.sample && all.isEmpty() && vm.index.progress.running ->
                    T("Reading locations on this phone… %,d of %,d".format(vm.index.progress.done, vm.index.progress.total), Sift.type.bodyS, color = c.onSurfaceVariant)
                !vm.sample && all.isEmpty() ->
                    T("Locations come from GPS saved in your photos. Allow \"Photo locations\" so Sift can read them.", Sift.type.bodyS, color = c.onSurfaceVariant)
                !network -> T("Map tiles are off, so only cached areas show. Turn them on in Settings → Privacy.", Sift.type.bodyS, color = c.onSurfaceVariant)
                failed -> T("You're offline. Areas you've seen before still show.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym("memory", size = 16.dp, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                T("Locations are read on this phone. Only map images are downloaded.", Sift.type.bodyS, Modifier.weight(1f), color = c.onSurfaceVariant)
            }
            T(
                "© OpenStreetMap contributors", Sift.type.data,
                Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) },
                color = c.onSurfaceVariant,
            )
        }
    }
}

/** Camera that fits all points: (world x, world y, zoom). Defaults to Europe when there are none. */
private fun fit(points: List<Located>): Triple<Double, Double, Float> {
    if (points.isEmpty()) return Triple(Mercator.x(10.0), Mercator.y(48.0), 4f)
    val xs = points.map { Mercator.x(it.lon) }
    val ys = points.map { Mercator.y(it.lat) }
    val span = maxOf(xs.max() - xs.min(), ys.max() - ys.min()).coerceAtLeast(0.0005)
    val zoom = log2(250.0 / span).toFloat().coerceIn(3f, 14f)
    return Triple((xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2, zoom)
}

private fun placeTitle(items: List<PhotoItem>): String {
    val first = items.minOf { it.dateMillis }
    val last = items.maxOf { it.dateMillis }
    val place = items.firstNotNullOfOrNull { it.place }
    val dates = if (DocParser.formatDay(first) == DocParser.formatDay(last)) DocParser.formatDay(first)
    else "${DocParser.formatDay(first)} – ${DocParser.formatDay(last)}"
    return place ?: dates
}

@Composable
fun PlaceScreen(vm: SiftViewModel, screen: Screen.Place) {
    val c = Sift.colors
    val context = LocalContext.current
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar(null, onBack = { vm.pop() })
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(screen.title, Sift.type.headlineS, color = c.onSurface)
                    T("%,d photos · %.4f, %.4f".format(screen.items.size, screen.lat, screen.lon), Sift.type.data, color = c.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Open in Maps", {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${screen.lat},${screen.lon}?q=${screen.lat},${screen.lon}"))) }
                                .onFailure { vm.toast.show("No maps app installed", icon = "info", onAction = null) }
                        }, kind = BtnKind.Tonal, icon = "directions")
                        Btn("Share", { vm.push(Screen.Share(screen.items)) }, kind = BtnKind.Text, icon = "share")
                    }
                }
            }
        }
        items(screen.items, key = { it.key }) { item ->
            PhotoCell(item, selectionMode = false, selected = false, onClick = { vm.push(Screen.Viewer(screen.items, screen.items.indexOf(item))) }, onLongClick = {})
        }
    }
}
