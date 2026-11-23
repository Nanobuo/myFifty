package com.nanobuo.myfifty.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.domain.Category
import com.nanobuo.myfifty.domain.Money
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Istogramma mensile; tocca una barra per vederne il valore. */
@Composable
fun MonthlyBarChart(data: List<Pair<YearMonth, Long>>, currency: String, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val max = (data.maxOf { it.second }).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val barSelected = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    var selected by remember(data) { mutableStateOf(data.lastIndex) }
    Column(modifier) {
        val sel = data.getOrNull(selected)
        if (sel != null) {
            Text(
                "${monthLabel(sel.first, long = true)}: ${Money.format(sel.second, currency)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).pointerInput(data) {
                detectTapGestures { o ->
                    val slot = size.width / data.size
                    selected = (o.x / slot).toInt().coerceIn(0, data.lastIndex)
                }
            }
        ) {
            val slot = size.width / data.size
            val w = slot * 0.62f
            data.forEachIndexed { i, (_, v) ->
                val x = i * slot + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(8f, 8f))
                val h = (v.coerceAtLeast(0).toFloat() / max) * size.height
                if (h > 0f) drawRoundRect(
                    if (i == selected) barSelected else bar,
                    Offset(x, size.height - h), Size(w, h), CornerRadius(8f, 8f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (m, _) ->
                Text(
                    monthLabel(m).take(3), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

/** Ciambella delle categorie con legenda. */
@Composable
fun CategoryDonut(items: List<Pair<Category, Long>>, currency: String, modifier: Modifier = Modifier) {
    val positive = items.filter { it.second > 0 }
    val total = positive.sumOf { it.second }.coerceAtLeast(1)
    val gap = MaterialTheme.colorScheme.surfaceContainerLow
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = size.minDimension * 0.18f
                val inset = stroke / 2
                var start = -90f
                positive.forEach { (c, v) ->
                    val sweep = 360f * v / total
                    drawArc(
                        Color(c.color), start, sweep, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                    if (positive.size > 1) drawArc(
                        gap, start + sweep - 0.8f, 1.6f, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke + 2f),
                    )
                    start += sweep
                }
            }
            Text(Money.format(total, currency).replace(",00", ""), style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            positive.take(8).forEach { (c, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                    Text(
                        c.label, Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (v * 100 / total == 0L) "<1%" else "${v * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Barra orizzontale proporzionale (es. quanto ha pagato ciascuno). */
@Composable
fun HBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(color))
    }
}

fun monthLabel(m: YearMonth, long: Boolean = false): String {
    val name = m.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
    return if (long) "$name ${m.year}" else name
}

