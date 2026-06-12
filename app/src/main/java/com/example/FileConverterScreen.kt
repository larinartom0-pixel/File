package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

// Iconic Retro 2011 Android (Gingerbread/HoneyComb) Palette Colors
val RetroBg = Color(0xFF151515) // Deep pitch back black
val RetroHeaderDarkGray = Color(0xFF2B2B2B) // Froyo / Gingerbread system grey
val RetroPanelBg = Color(0xFF1E1E1E) // Slate dark box frame
val RetroAccentGreen = Color(0xFFA4C639) // Original Android Toxic Green
val RetroAccentOrange = Color(0xFFFFA500) // Original Gingerbread Amber Orange
val RetroDividerGray = Color(0xFF383838) // CRT gray lines
val RetroLightBevel = Color(0xFF6F6F6F) // Button shines
val RetroDarkBevel = Color(0xFF121212) // Button shades
val RetroSolidGray = Color(0xFF4A4A4A) 
val RetroButtonText = Color(0xFFE5E5E5)

@Composable
fun FileConverterScreen(
    viewModel: ConversionViewModel,
    modifier: Modifier = Modifier
) {
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()

    if (isSettingsOpen) {
        RetroSettingsScreen(viewModel = viewModel)
        return
    }

    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val zipTrackers by viewModel.zipTrackers.collectAsState()
    val concurrencyLimit by viewModel.concurrencyLimit.collectAsState()
    val playingTaskId by viewModel.playingTaskId.collectAsState()

    var selectedFormat by remember { mutableStateOf("wav") }

    // Multi-File Picker contract
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (!uris.isNullOrEmpty()) {
                viewModel.addMultipleFilesForConversion(uris, selectedFormat)
                Toast.makeText(context, "Added ${uris.size} file(s) to queue", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // ZIP Archive Picker contract
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.addZipForConversion(uri, selectedFormat)
                Toast.makeText(context, "Extracting and scheduling ZIP contents...", Toast.LENGTH_LONG).show()
            }
        }
    )

    Scaffold(
        topBar = {
            // Retro 2.3 Gingerbread Style Title Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF3C3C3C), Color(0xFF222222))
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tiny retro green Android Robot indicator box
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(RetroAccentGreen, shape = RoundedCornerShape(3.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "a",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ANDROID TRANSCODER",
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Local Offline Engine v1.1.2 - Gingerbread Theme",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = RetroAccentGreen
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Retro-styled Setup Settings button
                        Box(
                            modifier = Modifier
                                .testTag("setup_settings_button")
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF5A5A5A), Color(0xFF333333))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(2.dp))
                                .clickable { viewModel.setSettingsOpen(true) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Setup Options",
                                    tint = RetroAccentGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "SETTINGS",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Retro-styled small clear history button
                        Box(
                            modifier = Modifier
                                .testTag("clear_history_button")
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF5A5A5A), Color(0xFF333333))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(2.dp))
                                .clickable { viewModel.clearHistory() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear History",
                                    tint = RetroAccentOrange,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "RESET",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                // Bright toxic green solid underline across screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(RetroAccentGreen)
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(RetroBg)
        ) {
            // Retro Tabbed Selector Box (Old Gingerbread UI layout)
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .background(RetroPanelBg, shape = RoundedCornerShape(2.dp))
                    .border(1.dp, RetroDividerGray, shape = RoundedCornerShape(2.dp))
                    .padding(12.dp)
            ) {
                // Section 1 Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(RetroAccentOrange)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1. CHOOSE OUTPUT CODEC",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Bar Selector (instead of rounded M3 filter chips)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val formats = listOf(
                        "wav" to "wav",
                        "m4a" to "m4a",
                        "mp3" to "mp3",
                        "ogg" to "ogg",
                        "flac" to "flac",
                        "mp4" to "mp4",
                        "mkv" to "mkv",
                        "mov" to "mov",
                        "webm" to "webm"
                    )
                    formats.forEach { (ext, label) ->
                        val isSelected = selectedFormat == ext
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF444444), Color(0xFF222222))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF333333), Color(0xFF1B1B1B))
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) RetroAccentGreen else RetroDividerGray
                                )
                                .clickable { selectedFormat = ext }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RetroAccentGreen else Color.Gray
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(2.dp)
                                            .background(RetroAccentGreen)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2 Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(RetroAccentOrange)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2. CONCURRENCY WORKERS (LIMIT)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Multi-Core CPU Schedulers [Max 3]:",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )

                    Row {
                        listOf(1, 2, 3).forEach { index ->
                            val active = concurrencyLimit == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(34.dp)
                                    .testTag("cpu_concurrency_$index")
                                    .background(
                                        if (active) {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF3A4E1F), Color(0xFF1F2F11))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF333333), Color(0xFF222222))
                                            )
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) RetroAccentGreen else RetroDividerGray
                                    )
                                    .clickable {
                                        viewModel.setConcurrencyLimit(index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = index.toString(),
                                        color = if (active) RetroAccentGreen else Color.DarkGray,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                    // A small retro "LED green light" on selector
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(5.dp)
                                            .background(
                                                if (active) RetroAccentGreen else Color.DarkGray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "HARDWARE REPORT: Detected ${viewModel.availableCores} CPU CORES.",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful beveled Retro action controls: "Add Files" & "Process ZIP"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Beveled button 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_files_button")
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF555555), Color(0xFF333333))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(BorderStroke(1.dp, Color(0xFF1A1A1A)))
                            .drawBehind {
                                // Light source overlay at top
                                drawLine(
                                    color = Color(0xFF888888),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = Color(0xFF888888),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 3f
                                )
                            }
                            .clickable { fileLauncher.launch(arrayOf("audio/*", "video/*")) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = RetroAccentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "ADD FILES (SD)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Beveled button 2
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("select_zip_button")
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF555555), Color(0xFF333333))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(BorderStroke(1.dp, Color(0xFF1A1A1A)))
                            .drawBehind {
                                // Light source overlay at top
                                drawLine(
                                    color = Color(0xFF888888),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = Color(0xFF888888),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 3f
                                )
                            }
                            .clickable { zipLauncher.launch(arrayOf("application/zip")) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = RetroAccentOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "UNPACK ZIP ARCHIVE",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Queue List header bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .border(
                        BorderStroke(1.dp, Color.Black),
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VINTAGE PROCESS QUEUE LOADER",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    if (tasks.isNotEmpty()) {
                        Text(
                            text = "[${tasks.size} IN QUEUE]",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RetroAccentGreen
                        )
                    }
                }
            }

            // Queue List / Empty placeholders
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "/* Empty Transcoding Queue */",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Insert media files above to feed local multi-threaded encoders. Supports audio/video. Non-network processing guarantees absolute terminal privacy.",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tasks.asReversed(), key = { it.id }) { task ->
                        ConversionTaskItem(
                            task = task,
                            isPlaying = playingTaskId == task.id,
                            onPlayClick = { viewModel.playAudio(task) },
                            onStopClick = { viewModel.stopAudio() },
                            zipTrackers = zipTrackers
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversionTaskItem(
    task: ConversionTask,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    zipTrackers: Map<String, ZipTracker>
) {
    val progressAnimated by animateFloatAsState(targetValue = task.progress / 100f, label = "progress")
    val context = LocalContext.current

    // Vintage panel layout for each item
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}")
            .background(Color(0xFF1F1F1F))
            .border(1.dp, Color(0xFF333333))
            .padding(10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // LED State indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when (task.status) {
                                ConversionStatus.PENDING -> Color.Gray
                                ConversionStatus.CONVERTING -> RetroAccentOrange
                                ConversionStatus.COMPLETED -> RetroAccentGreen
                                ConversionStatus.FAILED -> Color.Red
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))

                // File name & format
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.originalName,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXT: ${task.targetFormat.uppercase()}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = RetroAccentGreen
                        )
                        
                        if (task.parentZipName != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ZIP: ${task.parentZipName}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STATE: ${task.statusText}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Controls: Vintage media physical buttons for tasks
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        ConversionStatus.PENDING -> {
                            Text(
                                "[STANDBY]",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }
                        ConversionStatus.CONVERTING -> {
                            Text(
                                "[ACTIVE]",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = RetroAccentOrange
                            )
                        }
                        ConversionStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        ConversionStatus.COMPLETED -> {
                            // Metal grey play/stop button
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF666666), Color(0xFF333333))
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .border(1.dp, Color.Black, shape = RoundedCornerShape(2.dp))
                                    .clickable { if (isPlaying) onStopClick() else onPlayClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                                    contentDescription = "Audio Playback",
                                    tint = if (isPlaying) RetroAccentOrange else RetroAccentGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))

                            // Share export button
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF666666), Color(0xFF333333))
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .border(1.dp, Color.Black, shape = RoundedCornerShape(2.dp))
                                    .clickable { shareFile(context, task.outputFilePath) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export File",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Error info output terminal style
            if (task.status == ConversionStatus.FAILED && task.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A1515))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "STDOUT EXCEPTION > ${task.errorMessage}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Red,
                        lineHeight = 12.sp
                    )
                }
            }

            // Progress bar and details if active converting: Cylindrical classic progress tube
            if (task.status == ConversionStatus.CONVERTING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Vintage Canal Progress Bar: Black background canal with shiny green cylindrical bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(Color.Black)
                            .border(1.dp, Color(0xFF444444))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressAnimated)
                                .height(12.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFFC0FF3E),
                                            Color(0xFF76EE00),
                                            Color(0xFF458B00)
                                        )
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${task.progress.toInt()}%",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RetroAccentGreen
                    )
                }
            }

            // ZIP tracker repack alert
            if (task.parentZipId != null) {
                val tracker = zipTrackers[task.parentZipId]
                if (tracker != null && tracker.isCompletedAndRezipped && tracker.outputZipPath != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF152A15))
                            .border(1.dp, Color(0xFF2E5B2E))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(RetroAccentGreen, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ARCHIVE REPACK COMPLETED SUCESSFULLY",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = RetroAccentGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF4A884A), Color(0xFF1B4E1B))
                                        )
                                    )
                                    .border(1.dp, Color.Black)
                                    .clickable { shareFile(context, tracker.outputZipPath) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "SHARE",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareFile(context: Context, filePath: String?) {
    if (filePath == null) return
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Converted output file not found on disk", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            if (file.name.endsWith(".zip")) {
                type = "application/zip"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save or share converted file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RetroSettingsScreen(viewModel: ConversionViewModel) {
    val context = LocalContext.current
    val workingDirectoryUri by viewModel.workingDirectoryUri.collectAsState()
    val concurrencyLimit by viewModel.concurrencyLimit.collectAsState()

    // Activity launcher to pick parent output SAF document directory
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri ->
            if (treeUri != null) {
                try {
                    // Take persistable permission
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    viewModel.setWorkingDirectoryUri(treeUri)
                    Toast.makeText(context, "Storage folder locked successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to lock privileges: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // Standard Android media storage permission requester
    val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    var isPermissionGranted by remember {
        mutableStateOf(
            permissions.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            val allOk = results.values.all { it }
            isPermissionGranted = allOk
            if (allOk) {
                Toast.makeText(context, "Storage authorization GRANTED!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Storage auth REFUSED. Bulk file imports might fail.", Toast.LENGTH_LONG).show()
            }
        }
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF3C3C3C), Color(0xFF222222))
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tiny orange LED indicator box for settings mode
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(RetroAccentOrange, shape = RoundedCornerShape(3.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "s",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SYSTEM CONFIGURATION",
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Preferences & Encoders Setup Terminal",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = RetroAccentOrange
                            )
                        }
                    }

                    // Beveled "CLOSE" button
                    Box(
                        modifier = Modifier
                            .testTag("back_to_main_button")
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF5A5A5A), Color(0xFF333333))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(2.dp))
                            .clickable { viewModel.setSettingsOpen(false) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "< CLOSE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                // Solid Gingerbread orange underline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(RetroAccentOrange)
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(RetroBg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Panel 1: Security Authorization Permissions
            Column(
                modifier = Modifier
                    .background(RetroPanelBg, shape = RoundedCornerShape(2.dp))
                    .border(1.dp, RetroDividerGray, shape = RoundedCornerShape(2.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(RetroAccentGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SECURITY AND FILE SYSTEM PRVLEGES",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage / SD Privilege Access:",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (isPermissionGranted) RetroAccentGreen else Color.Red,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPermissionGranted) "[LED: GRANTED]" else "[LED: REQUIRED / DECLINED]",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isPermissionGranted) RetroAccentGreen else RetroAccentOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Request Button
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF555555), Color(0xFF333333))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(1.dp, Color(0xFF111111))
                            .clickable {
                                permissionLauncher.launch(permissions)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "REQUEST AUTH",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Panel 2: Choose Custom Working Directory Folder
            Column(
                modifier = Modifier
                    .background(RetroPanelBg, shape = RoundedCornerShape(2.dp))
                    .border(1.dp, RetroDividerGray, shape = RoundedCornerShape(2.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(RetroAccentGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WORKING EXPORT FOLDER (BACKUP TARGET)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "If selected, copies of all successfully transcoded files and unpacking ZIP output files are automatically saved directly inside this directory.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .border(1.dp, RetroDividerGray)
                        .padding(8.dp)
                ) {
                    val resolvedName = getFolderDisplayName(context, workingDirectoryUri)
                    Text(
                        text = "TARGET CHOSEN FOLDER >\n$resolvedName",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (workingDirectoryUri != null) RetroAccentGreen else Color.DarkGray,
                        lineHeight = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Change Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF555555), Color(0xFF333333))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(1.dp, Color(0xFF111111))
                            .clickable {
                                folderLauncher.launch(null)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "CHOOSE SAF FOLDER",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Reset default Button
                    if (workingDirectoryUri != null) {
                        Box(
                            modifier = Modifier
                                .weight(0.8f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF555555), Color(0xFF333333))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(1.dp, Color(0xFF111111))
                                .clickable {
                                    viewModel.clearWorkingDirectoryUri()
                                    Toast.makeText(context, "Reverted back to default cache path", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "RESET TO CACHE",
                                color = RetroAccentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Panel 3: Choose Queue Concurrency Pool limit (1 to 20)
            Column(
                modifier = Modifier
                    .background(RetroPanelBg, shape = RoundedCornerShape(2.dp))
                    .border(1.dp, RetroDividerGray, shape = RoundedCornerShape(2.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(RetroAccentGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONCURRENCY ENCODER THREAD POOL",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Maximum concurrent cpu transcodes in the queue loop:",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Decrement "-" button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF444444), Color(0xFF222222))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(1.dp, RetroDividerGray)
                                .clickable {
                                    if (concurrencyLimit > 1) {
                                        viewModel.setConcurrencyLimit(concurrencyLimit - 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "-",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Number indicator
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(28.dp)
                                .background(Color.Black)
                                .border(1.dp, RetroDividerGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format("%02d", concurrencyLimit),
                                color = RetroAccentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Increment "+" button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF444444), Color(0xFF222222))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(1.dp, RetroDividerGray)
                                .clickable {
                                    if (concurrencyLimit < 20) {
                                        viewModel.setConcurrencyLimit(concurrencyLimit + 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "High thread pools run parallel background transcoding workers. Supports up to 20 threads. Safely configure according to hardware performance.",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.DarkGray,
                    lineHeight = 12.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Tech stats decoration footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1C))
                    .border(1.dp, RetroDividerGray)
                    .padding(8.dp)
            ) {
                Text(
                    text = "OS: Android (SDK ${android.os.Build.VERSION.SDK_INT})",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
                Text(
                    text = "CPU CORES: ${viewModel.availableCores} Cores Detected Schedulers",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
                Text(
                    text = "CORE REALLOCATION: SAFE MODE",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = RetroAccentGreen
                )
            }
        }
    }
}

private fun getFolderDisplayName(context: Context, uriString: String?): String {
    if (uriString == null) return "System Default Sandbox Folder"
    val uri = android.net.Uri.parse(uriString)
    try {
        val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            uri,
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        )
        context.contentResolver.query(documentUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val displayName = cursor.getString(index)
                    if (!displayName.isNullOrBlank()) return displayName
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("SAF", "Error getting folder name", e)
    }
    return try {
        val lastPathSegment = uri.lastPathSegment ?: ""
        if (lastPathSegment.isNotEmpty()) {
            lastPathSegment.substringAfterLast(":")
        } else {
            "Custom Storage Folder"
        }
    } catch (e: Exception) {
        "Custom Storage Folder"
    }
}
