package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.GeoMath
import com.example.ui.DailyTravelSummary
import com.example.ui.WaymarkUiState
import com.example.ui.components.TripTimelineCard
import com.example.ui.components.WeeklyDistanceChart

@Composable
fun HistoryScreen(
    uiState: WaymarkUiState,
    onSelectDate: (String?) -> Unit,
    onTripClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = uiState.preferences.distanceUnit
    val weeklyTotalMeters = uiState.weeklyChartDays.sumOf { it.totalDistanceMeters }
    val selectedDate = uiState.selectedHistoryDateKey

    val visibleSummaries = if (selectedDate != null) {
        uiState.dailySummaries.filter { it.dateKey == selectedDate }
    } else {
        uiState.dailySummaries
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.history_weekly_overview),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = GeoMath.formatDistance(weeklyTotalMeters, unit) + " total",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (selectedDate != null) {
                            FilterChip(
                                selected = true,
                                onClick = { onSelectDate(null) },
                                label = { Text("All days") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear day filter",
                                    )
                                },
                                modifier = Modifier.testTag("clear_day_filter_chip"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    WeeklyDistanceChart(
                        days = uiState.weeklyChartDays,
                        unit = unit,
                        selectedDateKey = selectedDate,
                        onSelectDay = { onSelectDate(it) },
                    )
                }
            }
        }

        if (visibleSummaries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            visibleSummaries.forEach { summary ->
                item(key = "header_${summary.dateKey}") {
                    DailySummaryHeaderCard(
                        summary = summary,
                        unit = unit,
                    )
                }

                items(
                    items = summary.trips,
                    key = { "hist_trip_${it.id}" },
                ) { trip ->
                    TripTimelineCard(
                        trip = trip,
                        unit = unit,
                        onClick = { onTripClick(trip.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySummaryHeaderCard(
    summary: DailyTravelSummary,
    unit: com.example.domain.DistanceUnit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = summary.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${summary.tripCount} ${if (summary.tripCount == 1) "trip" else "trips"} • ${GeoMath.formatDuration(summary.totalDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = GeoMath.formatDistance(summary.totalDistanceMeters, unit),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
