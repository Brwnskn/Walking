package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DistanceUnit
import com.example.domain.GeoMath
import com.example.domain.RoutePoint
import com.example.ui.DailyTravelSummary
import com.example.ui.theme.Amber500
import com.example.ui.theme.Teal400
import com.example.ui.theme.Teal500
import kotlin.math.max

@Composable
fun RouteSparklineCanvas(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 3.5f,
    showGrid: Boolean = false,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val lineStartColor = Teal400
    val lineEndColor = Amber500

    Canvas(modifier = modifier) {
        if (showGrid) {
            val cols = 4
            val rows = 3
            for (c in 1 until cols) {
                val x = size.width * (c.toFloat() / cols)
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (r in 1 until rows) {
                val y = size.height * (r.toFloat() / rows)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }

        val pad = size.minDimension * 0.14f
        val drawW = (size.width - pad * 2f).coerceAtLeast(1f)
        val drawH = (size.height - pad * 2f).coerceAtLeast(1f)

        if (points.size < 2) {
            val start = Offset(pad, size.height * 0.65f)
            val end = Offset(size.width - pad, size.height * 0.35f)
            drawLine(
                brush = Brush.linearGradient(listOf(lineStartColor, lineEndColor), start, end),
                start = start,
                end = end,
                strokeWidth = strokeWidthDp * density,
                cap = StrokeCap.Round,
            )
            drawCircle(color = Teal500, radius = 5f * density, center = start)
            drawCircle(color = Amber500, radius = 5.5f * density, center = end)
            return@Canvas
        }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        val latSpan = (maxLat - minLat).coerceAtLeast(0.0002)
        val lngSpan = (maxLng - minLng).coerceAtLeast(0.0002)
        val maxSpan = max(latSpan, lngSpan)

        val xOffset = (drawW - (lngSpan / maxSpan * drawW).toFloat()) / 2f
        val yOffset = (drawH - (latSpan / maxSpan * drawH).toFloat()) / 2f

        val mapped = points.map { pt ->
            val nx = ((pt.longitude - minLng) / maxSpan).toFloat()
            val ny = ((maxLat - pt.latitude) / maxSpan).toFloat()
            Offset(
                x = pad + xOffset + nx * drawW,
                y = pad + yOffset + ny * drawH,
            )
        }

        val path = Path().apply {
            moveTo(mapped.first().x, mapped.first().y)
            for (i in 1 until mapped.size) {
                lineTo(mapped[i].x, mapped[i].y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(lineStartColor, lineEndColor),
                start = mapped.first(),
                end = mapped.last(),
            ),
            style = Stroke(
                width = strokeWidthDp * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Start marker
        drawCircle(
            color = Color.White,
            radius = 6f * density,
            center = mapped.first(),
        )
        drawCircle(
            color = Teal500,
            radius = 4.2f * density,
            center = mapped.first(),
        )

        // Destination marker
        drawCircle(
            color = Color.White,
            radius = 6.5f * density,
            center = mapped.last(),
        )
        drawCircle(
            color = Amber500,
            radius = 4.6f * density,
            center = mapped.last(),
        )
    }
}

@Composable
fun WeeklyDistanceChart(
    days: List<DailyTravelSummary>,
    unit: DistanceUnit,
    selectedDateKey: String?,
    onSelectDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxMeters = days.maxOfOrNull { it.totalDistanceMeters }?.coerceAtLeast(1000.0) ?: 1000.0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val ratio = (day.totalDistanceMeters / maxMeters).toFloat().coerceIn(0f, 1f)
            val isSelected = selectedDateKey == day.dateKey
            val barColor = when {
                isSelected -> Amber500
                day.totalDistanceMeters > 0.0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectDay(day.dateKey) }
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .testTag("chart_day_${day.dateKey}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = if (day.totalDistanceMeters > 0.0) {
                        GeoMath.formatDistanceValue(day.totalDistanceMeters, unit)
                    } else {
                        "0"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .fillMaxHeight(fraction = max(0.08f, ratio))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(barColor),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = day.shortWeekday,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
