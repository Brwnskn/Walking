package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.DistanceUnit
import com.example.domain.GeoMath
import com.example.domain.LiveTrackerState
import com.example.domain.TrackerPhase
import com.example.domain.TravelMode
import com.example.ui.WaymarkUiState
import com.example.ui.components.RouteSparklineCanvas
import com.example.ui.components.TripTimelineCard
import com.example.ui.theme.Amber500
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.Teal400
import com.example.ui.theme.Teal500

@Composable
fun TodayScreen(
    uiState: WaymarkUiState,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onFinishLegNow: () -> Unit,
    onToggleUnit: (DistanceUnit) -> Unit,
    onTripClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(checkLocationPermission(context))
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            checkLocationPermission(context)
        hasLocationPermission = granted
        if (granted) {
            onStartTracking()
        }
    }

    val unit = uiState.preferences.distanceUnit
    val live = uiState.liveState

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroOdometerCard(
                totalDistanceMeters = uiState.todayTotalDistanceMeters,
                totalDurationMs = uiState.todayTotalDurationMs,
                completedCount = uiState.todayCompletedTrips.size,
                dwellMinutes = uiState.preferences.dwellThresholdMinutes,
                unit = unit,
                isTracking = live.isTrackingActive,
                onToggleUnit = {
                    val next = if (unit == DistanceUnit.MILES) DistanceUnit.KILOMETERS else DistanceUnit.MILES
                    onToggleUnit(next)
                },
                onToggleTracking = {
                    if (live.isTrackingActive) {
                        onStopTracking()
                    } else if (checkLocationPermission(context)) {
                        hasLocationPermission = true
                        onStartTracking()
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                },
            )
        }

        if (!hasLocationPermission) {
            item {
                PermissionPromptCard(
                    onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = live.isTrackingActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                LiveLegTelemetryCard(
                    liveState = live,
                    unit = unit,
                    nowMs = uiState.nowMs,
                    onFinishLegNow = onFinishLegNow,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Point-to-Point Journeys",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${uiState.todayCompletedTrips.size} completed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.todayCompletedTrips.isEmpty()) {
            item {
                EmptyTodayTripsCard()
            }
        } else {
            items(
                items = uiState.todayCompletedTrips,
                key = { it.id },
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

@Composable
private fun HeroOdometerCard(
    totalDistanceMeters: Double,
    totalDurationMs: Long,
    completedCount: Int,
    dwellMinutes: Int,
    unit: DistanceUnit,
    isTracking: Boolean,
    onToggleUnit: () -> Unit,
    onToggleTracking: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_route_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.18f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Slate950.copy(alpha = 0.72f),
                                Slate900.copy(alpha = 0.94f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.today_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Text(
                            text = stringResource(R.string.today_subtitle_combined),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Teal400,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleUnit)
                            .testTag("unit_toggle_chip"),
                    ) {
                        Text(
                            text = unit.shortLabel.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = GeoMath.formatDistanceValue(totalDistanceMeters, unit),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        modifier = Modifier.testTag("today_distance_readout"),
                    )
                    Text(
                        text = unit.shortLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HeroStatChip(
                        icon = Icons.Filled.Schedule,
                        label = GeoMath.formatDuration(totalDurationMs),
                        caption = "Travel time",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStatChip(
                        icon = Icons.Filled.Route,
                        label = "$completedCount",
                        caption = if (completedCount == 1) "Leg" else "Legs",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStatChip(
                        icon = Icons.Filled.Flag,
                        label = ">${dwellMinutes}m",
                        caption = "Stop rule",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onToggleTracking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("tracking_toggle_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) Amber500 else Teal500,
                        contentColor = Slate950,
                    ),
                ) {
                    Icon(
                        imageVector = if (isTracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTracking) {
                            stringResource(R.string.stop_tracking)
                        } else {
                            stringResource(R.string.start_tracking)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    icon: ImageVector,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.09f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Teal400,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LiveLegTelemetryCard(
    liveState: LiveTrackerState,
    unit: DistanceUnit,
    nowMs: Long,
    onFinishLegNow: () -> Unit,
) {
    val phaseLabel = when (liveState.phase) {
        TrackerPhase.IDLE -> stringResource(R.string.status_idle)
        TrackerPhase.MOVING -> stringResource(R.string.status_moving)
        TrackerPhase.DWELLING -> stringResource(R.string.status_dwelling)
        TrackerPhase.AT_DESTINATION -> stringResource(R.string.status_at_destination)
    }
    val statusDotColor = when (liveState.phase) {
        TrackerPhase.MOVING -> Teal500
        TrackerPhase.DWELLING -> Amber500
        TrackerPhase.AT_DESTINATION -> MaterialTheme.colorScheme.tertiary
        TrackerPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val modeIcon = when (liveState.inferredMode) {
        TravelMode.BIKE -> Icons.Filled.DirectionsBike
        TravelMode.CAR -> Icons.Filled.DirectionsCar
        TravelMode.TRAVEL -> Icons.Filled.Route
    }

    val legElapsedMs = if (
        (liveState.phase == TrackerPhase.MOVING || liveState.phase == TrackerPhase.DWELLING) &&
        liveState.activeTripStartMs != null
    ) {
        val endTime = if (liveState.phase == TrackerPhase.DWELLING && liveState.dwellStartMs != null) {
            liveState.dwellStartMs
        } else {
            nowMs
        }
        (endTime - liveState.activeTripStartMs).coerceAtLeast(0L)
    } else {
        0L
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_telemetry_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (liveState.phase == TrackerPhase.MOVING || liveState.phase == TrackerPhase.DWELLING) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = liveState.inferredMode.label,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = liveState.inferredMode.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            if (liveState.phase == TrackerPhase.MOVING || liveState.phase == TrackerPhase.DWELLING) {
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT LEG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = GeoMath.formatDistance(liveState.activeDistanceMeters, unit),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = GeoMath.formatDuration(legElapsedMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = GeoMath.formatSpeed(liveState.currentSpeedMps, unit),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    RouteSparklineCanvas(
                        points = liveState.activeRoutePoints,
                        modifier = Modifier
                            .size(width = 104.dp, height = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    )
                }

                if (liveState.phase == TrackerPhase.DWELLING) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PauseCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Confirming destination",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                Text(
                                    text = "${GeoMath.formatCountdown(liveState.dwellRemainingMs)} left",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { liveState.dwellProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Amber500,
                                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f),
                            )
                        }
                    }
                }

                if (liveState.activeDistanceMeters >= 50.0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onFinishLegNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("lock_destination_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.finish_leg_now),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Listening for movement by car or bike. A new leg begins automatically once you depart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionPromptCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("grant_location_button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.permission_grant_cta))
            }
        }
    }
}

@Composable
private fun EmptyTodayTripsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_route_banner),
                contentDescription = stringResource(R.string.cd_route_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
            )
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.empty_trips_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.empty_trips_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun checkLocationPermission(context: android.content.Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}
