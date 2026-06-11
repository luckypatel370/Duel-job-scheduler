package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.CalendarContract
import com.example.data.model.Job
import com.example.data.model.Shift
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Color choices for Job Profiles (specifically Walmart Blue and Coop Red)
val JOB_COLORS = listOf(
    "#1E88E5" to Color(0xFF1E88E5), // Walmart Blue
    "#E53935" to Color(0xFFE53935), // Coop Red
    "#48CFAD" to Color(0xFF48CFAD), // Teal
    "#2E7D32" to Color(0xFF2E7D32), // Forest Green
    "#F9A825" to Color(0xFFF9A825), // Amber Gold
    "#8E24AA" to Color(0xFF8E24AA), // Lavender / Purple
    "#00838F" to Color(0xFF00838F), // Cyan / Dark Teal
    "#D81B60" to Color(0xFFD81B60)  // Pink Rose
)

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF6200EE)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobScheduleScreen(
    viewModel: JobViewModel,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val todaysShifts by viewModel.selectedDateShifts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showAddJobDialog by remember { mutableStateOf(false) }
    var showAddShiftDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Schedule, 1 = Job Profiles

    // Date range preparation for calendar strip (centered around selected/today)
    val today = remember { LocalDate.now() }
    val dates = remember { (-3..11).map { today.plusDays(it.toLong()) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Daily Schedule",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val currentDateStr = remember(selectedDate) {
                            try {
                                val parsedDate = LocalDate.parse(selectedDate)
                                parsedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"))
                            } catch (e: Exception) {
                                "Job schedule"
                            }
                        }
                        Text(
                            text = currentDateStr,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.recalculateTransit(selectedDate) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(40.dp)
                            .testTag("refresh_transit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync and recalculate travel times",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = {
                        if (jobs.isEmpty()) {
                            showAddJobDialog = true
                        } else {
                            showAddShiftDialog = true
                        }
                    },
                    modifier = Modifier.testTag("add_shift_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Scheduled Shift")
                }
            } else {
                FloatingActionButton(
                    onClick = { showAddJobDialog = true },
                    modifier = Modifier.testTag("add_job_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Job Profile")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // High-fidelity Stats Board
            StatsHeaderBoard(todaysShifts = todaysShifts, allShifts = shifts, jobs = jobs)

            // Segmented Tab bar to switch between Timeline and Profiles
            SegmentedTabs(
                selectedTab = activeTab,
                onTabSelected = { activeTab = it }
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (activeTab == 0) {
                val upcomingShift = remember(shifts) {
                    val nowStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    shifts.mapNotNull { s ->
                        try {
                            val combinedStr = "${s.date} ${s.startTime}"
                            combinedStr to s
                        } catch (e: Exception) {
                            null
                        }
                    }.filter { it.first >= nowStr }
                    .minByOrNull { it.first }?.second
                }

                if (upcomingShift != null) {
                    UpcomingShiftWidget(
                        upcomingShift = upcomingShift,
                        jobs = jobs,
                        onDateSelected = { viewModel.selectDate(it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Horizontal scroll calendar strip
                CalendarStrip(
                    dates = dates,
                    selectedDate = selectedDate,
                    onDateSelected = { viewModel.selectDate(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (todaysShifts.isEmpty()) {
                    EmptyTimelineState(
                        hasJobs = jobs.isNotEmpty(),
                        onCreateJob = { showAddJobDialog = true },
                        onAddShift = { showAddShiftDialog = true }
                    )
                } else {
                    TimelineList(
                        shifts = todaysShifts,
                        jobs = jobs,
                        onDeleteShift = { viewModel.deleteShift(it) }
                    )
                }
            } else {
                if (jobs.isEmpty()) {
                    EmptyJobState(onCreateJob = { showAddJobDialog = true })
                } else {
                    JobProfilesList(
                        jobs = jobs,
                        onDeleteJob = { viewModel.deleteJob(it) }
                    )
                }
            }
        }
    }

    // Modal Dialogs
    if (showAddJobDialog) {
        AddJobDialog(
            onDismiss = { showAddJobDialog = false },
            onConfirm = { title, company, address, rate, color ->
                viewModel.addJob(title, company, address, rate, color)
                showAddJobDialog = false
            }
        )
    }

    if (showAddShiftDialog) {
        AddShiftDialog(
            jobs = jobs,
            defaultDate = selectedDate,
            onDismiss = { showAddShiftDialog = false },
            onConfirm = { jobId, date, start, end, mode, notes ->
                viewModel.addShift(jobId, date, start, end, mode, notes)
                showAddShiftDialog = false
            }
        )
    }
}

@Composable
fun SegmentedTabs(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf("Work Schedule", "My Employers")
        tabs.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun StatsHeaderBoard(
    todaysShifts: List<Shift>,
    allShifts: List<Shift>,
    jobs: List<Job>
) {
    // Math to compute current daily earnings & total transit minutes
    val totalEarnings = remember(todaysShifts, jobs) {
        todaysShifts.sumOf { shift ->
            val job = jobs.firstOrNull { it.id == shift.jobId } ?: return@sumOf 0.0
            val hours = calculateHoursDecimal(shift.startTime, shift.endTime)
            hours * job.hourlyRate
        }
    }

    val totalTransitMinutes = remember(todaysShifts) {
        todaysShifts.sumOf { it.transitTimeToNext ?: 0 }
    }

    val totalWeeklyHours = remember(allShifts) {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val sunday = monday.plusDays(6)
        
        allShifts.filter {
            try {
                val shiftDate = LocalDate.parse(it.date)
                !shiftDate.isBefore(monday) && !shiftDate.isAfter(sunday)
            } catch (e: Exception) {
                false
            }
        }.sumOf { shift ->
            calculateHoursDecimal(shift.startTime, shift.endTime)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = "DAILY EARNED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", totalEarnings)}",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Simple divider
            Box(
                modifier = Modifier
                    .height(35.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "WEEKLY HOURS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "%.1f", totalWeeklyHours),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " hrs",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Simple divider
            Box(
                modifier = Modifier
                    .height(35.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "DAILY COMMUTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (totalTransitMinutes > 0) "$totalTransitMinutes" else "0",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (totalTransitMinutes > 60) Color(0xFFFC6E51) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " min",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarStrip(
    dates: List<LocalDate>,
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEE dd")
    val selectedLocalDate = try {
        LocalDate.parse(selectedDate)
    } catch (e: Exception) {
        LocalDate.now()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Select Work Date",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dates) { date ->
                val isSelected = date == selectedLocalDate
                val dayStr = date.format(formatter)
                val parts = dayStr.split(" ")

                Column(
                    modifier = Modifier
                        .width(58.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onDateSelected(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = parts[0].uppercase(),
                        fontSize = 11.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = parts[1],
                        fontSize = 18.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun ColumnScope.TimelineList(
    shifts: List<Shift>,
    jobs: List<Job>,
    onDeleteShift: (Shift) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(shifts.size) { index ->
            val shift = shifts[index]
            val job = jobs.firstOrNull { it.id == shift.jobId }

            ShiftItemRow(
                shift = shift,
                job = job,
                onDeleteShift = { onDeleteShift(shift) }
            )

            // If there's a next shift, show the travel path travel details
            if (index < shifts.size - 1) {
                val nextShift = shifts[index + 1]
                val nextJob = jobs.firstOrNull { it.id == nextShift.jobId }
                
                TravelTransitionPath(
                    currentShift = shift,
                    currentJob = job,
                    nextShift = nextShift,
                    nextJob = nextJob
                )
            }
        }
    }
}

@Composable
fun ShiftItemRow(
    shift: Shift,
    job: Job?,
    onDeleteShift: () -> Unit
) {
    val jobColor = job?.let { parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("shift_card_${shift.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, jobColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant vertical timeline clock column
            Column(
                modifier = Modifier
                    .width(65.dp)
                    .padding(end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = shift.startTime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Vertical connection track
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 2.dp)
                )
                
                Text(
                    text = shift.endTime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = job?.title ?: "Unknown Position",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = job?.company ?: "Direct Schedule",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Address",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = job?.address ?: "No location, remote",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Active Transit Mode pill tag
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = jobColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, jobColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = (shift.transitMode ?: "Driving").uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = jobColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Earnings and deletion details
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (job != null) {
                    val hours = calculateHoursDecimal(shift.startTime, shift.endTime)
                    val earned = hours * job.hourlyRate
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", earned)}",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.1f", job.hourlyRate)}/hr",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = { exportShiftToCalendar(context, shift, job) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(28.dp)
                            .testTag("export_shift_button_${shift.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Export to Calendar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteShift,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Scheduled Shift",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TravelTransitionPath(
    currentShift: Shift,
    currentJob: Job?,
    nextShift: Shift,
    nextJob: Job?
) {
    val gapMin = calculateGapMinutes(currentShift.endTime, nextShift.startTime)
    val transitTime = currentShift.transitTimeToNext ?: 0
    val transitDistance = currentShift.transitDistanceToNext
    val transitMode = currentShift.transitMode ?: "Driving"
    val transitSummary = currentShift.transitSummary
    val isLate = transitTime > gapMin

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left dashed connector
        Canvas(modifier = Modifier.weight(0.15f).height(1.dp)) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(
                color = Color(0xFFC4C6D0),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f,
                pathEffect = pathEffect
            )
        }

        // Center stylish panel representing transit
        val (bgColor, textColor, borderColor) = if (isLate) {
            Triple(Color(0xFFFEEFC3), Color(0xFF7C5800), Color(0xFFFAD26C))
        } else {
            Triple(Color(0xFFD4E3FF), Color(0xFF0061A4), Color(0xFFBAC7DB))
        }

        Card(
            modifier = Modifier
                .weight(0.7f)
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = bgColor
            ),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(24.dp) // Sleek Pill Shape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val modeEmoji = when (transitMode) {
                        "Transit" -> "🚌"
                        "Bicycling" -> "🚲"
                        "Walking" -> "🚶"
                        else -> "🚗"
                    }
                    Text(
                        text = modeEmoji,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val durationText = "${transitTime} min"
                        val distanceText = if (transitDistance != null) "${String.format(Locale.US, "%.1f", transitDistance)} mi" else ""
                        val mainLabel = "$durationText ${transitMode.lowercase()}" + (if (distanceText.isNotEmpty()) " ($distanceText)" else "")
                        Text(
                            text = mainLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (!transitSummary.isNullOrEmpty()) {
                            Text(
                                text = transitSummary,
                                fontSize = 9.sp,
                                color = textColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Overlap warning badge inside
                if (isLate) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE9573F), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "OVERLAP",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else if (currentShift.isAIEstimated) {
                    Box(
                        modifier = Modifier
                            .background(textColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "AI EST",
                            color = textColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Right dashed connector
        Canvas(modifier = Modifier.weight(0.15f).height(1.dp)) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(
                color = Color(0xFFC4C6D0),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f,
                pathEffect = pathEffect
            )
        }
    }
}

@Composable
fun ColumnScope.EmptyTimelineState(
    hasJobs: Boolean,
    onCreateJob: () -> Unit,
    onAddShift: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No shifts scheduled today",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Track layouts, overlap timings containing travel alerts instantly.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (hasJobs) {
                Button(
                    onClick = onAddShift,
                    modifier = Modifier.testTag("empty_add_shift_button")
                ) {
                    Text(text = "Schedule Shift")
                }
            } else {
                Button(
                    onClick = onCreateJob,
                    modifier = Modifier.testTag("empty_create_job_button")
                ) {
                    Text(text = "Create Employer Job Profile")
                }
            }
        }
    }
}

@Composable
fun ColumnScope.JobProfilesList(
    jobs: List<Job>,
    onDeleteJob: (Job) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(jobs) { job ->
            val color = parseHexColor(job.colorHex)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("job_card_${job.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Accent circle
                    Box(
                        modifier = Modifier
                            .background(color, CircleShape)
                            .size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = job.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = job.company,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Address",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = job.address,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", job.hourlyRate)}/hr",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        IconButton(
                            onClick = { onDeleteJob(job) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Job Profile",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.EmptyJobState(onCreateJob: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No employer profiles created yet",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Create templates with standard addresses, rates and color categories.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onCreateJob,
                modifier = Modifier.testTag("empty_create_first_job_button")
            ) {
                Text(text = "Add Employer Profile")
            }
        }
    }
}

@Composable
fun AddJobDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, company: String, address: String, rate: Double, color: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var selectedColorCode by remember { mutableStateOf(JOB_COLORS[0].first) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "New Employer Profile",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Job Position / Title") },
                    placeholder = { Text("e.g. Barista") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_title_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Employer") },
                    placeholder = { Text("e.g. Starbucks") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_company_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Job Address / Workplace") },
                    placeholder = { Text("e.g. 120 Sansome St, San Francisco") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_address_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Hourly Rate ($)") },
                    placeholder = { Text("22.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_rate_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Color Selection Row
                Text(
                    text = "Choose Label Accent",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JOB_COLORS.forEach { colorPair ->
                        val colorHex = colorPair.first
                        val isSelected = selectedColorCode == colorHex
                        Box(
                            modifier = Modifier
                                .background(colorPair.second, CircleShape)
                                .size(28.dp)
                                .border(
                                    if (isSelected) 3.dp else 0.dp,
                                    if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColorCode = colorHex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val doubleRate = rate.toDoubleOrNull() ?: 0.0
                            if (title.isNotBlank() && address.isNotBlank()) {
                                onConfirm(title, company, address, doubleRate, selectedColorCode)
                            }
                        },
                        enabled = title.isNotBlank() && address.isNotBlank(),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("save_job_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun AddShiftDialog(
    jobs: List<Job>,
    defaultDate: String,
    onDismiss: () -> Unit,
    onConfirm: (jobId: Int, date: String, start: String, end: String, mode: String, notes: String) -> Unit
) {
    var selectedJobIndex by remember { mutableStateOf(0) }
    var inputDate by remember { mutableStateOf(defaultDate) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("17:00") }
    var travelMode by remember { mutableStateOf("Driving") }
    var notes by remember { mutableStateOf("") }

    val travelModes = listOf("Driving", "Transit", "Bicycling", "Walking")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Schedule Shift",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Select Job profile picker
                Text(
                    text = "Select Employer Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                // Horizontal row of existing employer template profiles
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(jobs.size) { index ->
                        val job = jobs[index]
                        val isSelected = selectedJobIndex == index
                        Card(
                            modifier = Modifier
                                .clickable { selectedJobIndex = index }
                                .widthIn(min = 100.dp),
                            border = BorderStroke(
                                2.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = job.company,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = job.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputDate,
                    onValueChange = { inputDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    placeholder = { Text("e.g. 2026-06-12") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shift_date_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (24h HH:MM)") },
                        placeholder = { Text("09:00") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                            .testTag("shift_start_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (24h HH:MM)") },
                        placeholder = { Text("17:00") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                            .testTag("shift_end_input"),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Select Travel Mode picker
                Text(
                    text = "Mode of Travel to Next Stop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    travelModes.forEach { mode ->
                        val isSelected = travelMode == mode
                        val emoji = when (mode) {
                            "Transit" -> "🚌"
                            "Bicycling" -> "🚲"
                            "Walking" -> "🚶"
                            else -> "🚗"
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clickable { travelMode = mode },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = emoji, fontSize = 16.sp)
                                Text(
                                    text = mode,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Shift Notes (Optional)") },
                    placeholder = { Text("e.g. Bring work boots") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shift_notes_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val jobIndex = selectedJobIndex.coerceIn(0, jobs.size - 1)
                            val jobId = jobs[jobIndex].id
                            onConfirm(jobId, inputDate, startTime, endTime, travelMode, notes)
                        },
                        enabled = inputDate.isNotBlank() && startTime.isNotBlank() && endTime.isNotBlank() && jobs.isNotEmpty(),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("save_shift_button")
                    ) {
                        Text("Schedule")
                    }
                }
            }
        }
    }
}

// Time calculation utility helpers
fun calculateHoursDecimal(start: String, end: String): Double {
    return try {
        val format = DateTimeFormatter.ofPattern("HH:mm")
        val startTime = LocalTime.parse(start, format)
        val endTime = LocalTime.parse(end, format)
        val duration = java.time.Duration.between(startTime, endTime)
        val minutes = duration.toMinutes()
        minutes.toDouble() / 60.0
    } catch (e: Exception) {
        8.0 // default
    }
}

fun calculateGapMinutes(endTimeStr: String, startTimeStr: String): Int {
    return try {
        val format = DateTimeFormatter.ofPattern("HH:mm")
        val end = LocalTime.parse(endTimeStr, format)
        val start = LocalTime.parse(startTimeStr, format)
        
        // If start time is before end time, we assume crossing midnight or standard next day shift
        val duration = java.time.Duration.between(end, start)
        duration.toMinutes().toInt()
    } catch (e: Exception) {
        0
    }
}

fun exportShiftToCalendar(context: android.content.Context, shift: Shift, job: Job?) {
    val title = "${job?.title ?: "Shift"} at ${job?.company ?: "Work"}"
    val description = "Shift Notes: ${shift.notes}\nTransit Mode: ${shift.transitMode ?: "None"}"
    val location = job?.address ?: ""
    
    try {
        val dateParser = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val localDate = LocalDate.parse(shift.date, dateParser)
        
        val timeParser = DateTimeFormatter.ofPattern("HH:mm")
        val startTime = LocalTime.parse(shift.startTime, timeParser)
        val endTime = LocalTime.parse(shift.endTime, timeParser)
        
        val startDateTime = LocalDateTime.of(localDate, startTime)
        var endDateTime = LocalDateTime.of(localDate, endTime)
        
        // If end time is before start time, it likely spans into the next day
        if (endTime.isBefore(startTime)) {
            endDateTime = endDateTime.plusDays(1)
        }
        
        val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.Events.ALL_DAY, false)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun UpcomingShiftWidget(
    upcomingShift: Shift,
    jobs: List<Job>,
    onDateSelected: (String) -> Unit
) {
    val job = jobs.firstOrNull { it.id == upcomingShift.jobId }
    val jobColor = job?.let { parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onDateSelected(upcomingShift.date) }
            .testTag("upcoming_shift_widget"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = jobColor.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.5.dp, jobColor.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Upcoming Shift",
                        tint = jobColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEXT UPCOMING SHIFT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = jobColor,
                        letterSpacing = 1.sp
                    )
                }
                
                val dateStr = try {
                    val date = LocalDate.parse(upcomingShift.date)
                    date.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"))
                } catch (e: Exception) {
                    upcomingShift.date
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = jobColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = jobColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(jobColor.copy(alpha = 0.15f), CircleShape)
                        .size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val modeEmoji = when (upcomingShift.transitMode) {
                        "Transit" -> "🚌"
                        "Bicycling" -> "🚲"
                        "Walking" -> "🚶"
                        else -> "🚗"
                    }
                    Text(text = modeEmoji, fontSize = 18.sp)
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job?.title ?: "Scheduled Shift",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = job?.company ?: "Employer",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Timings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${upcomingShift.startTime} - ${upcomingShift.endTime}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (job != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        val hours = calculateHoursDecimal(upcomingShift.startTime, upcomingShift.endTime)
                        val earned = hours * job.hourlyRate
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", earned)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = jobColor
                        )
                        Text(
                            text = "Est. Earnings",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
