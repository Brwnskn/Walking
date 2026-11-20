package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.Trip
import com.example.domain.DistanceUnit
import com.example.domain.GeoMath
import com.example.domain.TravelMode
import com.example.ui.components.RouteSparklineCanvas
import com.example.ui.theme.Amber500
import com.example.ui.theme.Teal500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    trip: Trip?,
    unit: DistanceUnit,
    onBack: () -> Unit,
    onRenameLabels: (String, String) -> Unit,
    onDeleteTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (trip != null) {
                        IconButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier.testTag("detail_rename_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename origin or destination",
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("detail_delete_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete trip",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (trip == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Trip not found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val modeIcon = when (trip.travelMode) {
            TravelMode.BIKE -> Icons.Filled.DirectionsBike
            TravelMode.CAR -> Icons.Filled.DirectionsCar
            TravelMode.TRAVEL -> Icons.Filled.Route
        }
        val briefPauseMs = (trip.durationMs - trip.movingDurationMs).coerceAtLeast(0L)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        RouteSparklineCanvas(
                            points = trip.routePoints,
                            strokeWidthDp = 4.5f,
                            showGrid = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        EndpointDetailRow(
                            color = Teal500,
                            caption = "DEPARTED • ${GeoMath.formatClockTime(trip.startTimeMs)}",
                            title = trip.originLabel,
                            coords = GeoMath.formatCoordFallback(trip.originLat, trip.originLng),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        EndpointDetailRow(
                            color = Amber500,
                            caption = "ARRIVED (STOPPED >15M) • ${GeoMath.formatClockTime(trip.arrivalTimeMs)}",
                            title = trip.destinationLabel,
                            coords = GeoMath.formatCoordFallback(trip.destinationLat, trip.destinationLng),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricBox(
                        icon = Icons.Filled.Schedule,
                        title = "Point-to-Point Time",
                        value = GeoMath.formatDuration(trip.durationMs),
                        subtitle = "Origin to destination",
                        modifier = Modifier.weight(1f),
                    )
                    MetricBox(
                        icon = Icons.Filled.Route,
                        title = "Distance",
                        value = GeoMath.formatDistance(trip.distanceMeters, unit),
                        subtitle = trip.travelMode.label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricBox(
                        icon = Icons.Filled.Speed,
                        title = "Average Speed",
                        value = GeoMath.formatSpeed(trip.avgSpeedMps, unit),
                        subtitle = "Peak ${GeoMath.formatSpeed(trip.peakSpeedMps, unit)}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricBox(
                        icon = modeIcon,
                        title = "Moving vs Pauses",
                        value = GeoMath.formatDuration(trip.movingDurationMs),
                        subtitle = "Brief stops ${GeoMath.formatDuration(briefPauseMs)}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rename_stops_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Label Start & Destination Points")
                }
            }
        }

        if (showRenameDialog) {
            var originInput by remember(trip.id) { mutableStateOf(trip.originLabel) }
            var destInput by remember(trip.id) { mutableStateOf(trip.destinationLabel) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Waypoints") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = originInput,
                            onValueChange = { originInput = it },
                            label = { Text("Start point (e.g. Home)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = destInput,
                            onValueChange = { destInput = it },
                            label = { Text("Destination (e.g. Office)") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRenameLabels(originInput, destInput)
                            showRenameDialog = false
                        },
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete this trip?") },
                text = { Text("This removes this point-to-point journey from your daily total.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteTrip()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun EndpointDetailRow(
    color: androidx.compose.ui.graphics.Color,
    caption: String,
    title: String,
    coords: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = coords,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
