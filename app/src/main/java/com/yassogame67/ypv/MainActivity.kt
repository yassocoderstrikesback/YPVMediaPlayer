package com.yassogame67.ypv

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yassogame67.ypv.ui.theme.YpvTheme
import okhttp3.OkHttpClient
import java.io.Serializable

@OptIn(UnstableApi::class)
data class PlayerSettings(
    val useHardwareAcceleration: Boolean = true,
    val highPerformanceMode: Boolean = true,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) : Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YpvTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp()
                }
            }
        }
    }
}

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(videoUrl: String, settings: PlayerSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }

    var showTrackSelector by remember { mutableStateOf<Int?>(null) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }

    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    val exoPlayer = remember(videoUrl, settings) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                forceEnableMediaCodecAsynchronousQueueing()
            }
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMatroskaExtractorFlags(0)

        // FIX: Use OkHttp for much better network seeking support
        val okHttpClient = OkHttpClient.Builder().build()
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(Util.getUserAgent(context, "YPV-Player"))
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (settings.highPerformanceMode) 15000 else 8000,
                if (settings.highPerformanceMode) 50000 else 30000,
                2500,
                5000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)

                setSeekParameters(SeekParameters.DEFAULT)

                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        currentTracks = tracks
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                        onBack()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            Log.d("YPV", "Seekable: $isCurrentMediaItemSeekable, Duration: $duration")
                        }
                    }
                })
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        if (showTrackSelector != null) showTrackSelector = null else onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { playerView ->
                // CRITICAL FIX: Update the player instance in the view
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = settings.resizeMode
                playerView.setShowFastForwardButton(true)
                playerView.setShowRewindButton(true)
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Row {
                IconButton(onClick = { showTrackSelector = C.TRACK_TYPE_AUDIO }) {
                    Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White)
                }
                IconButton(onClick = { showTrackSelector = C.TRACK_TYPE_TEXT }) {
                    Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White)
                }
            }
        }

        if (showTrackSelector != null) {
            TrackSelectionDialog(
                trackType = showTrackSelector!!,
                tracks = currentTracks,
                onTrackSelected = { group, trackIndex ->
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                        .build()
                    showTrackSelector = null
                },
                onDismiss = { showTrackSelector = null }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TrackSelectionDialog(trackType: Int, tracks: Tracks, onTrackSelected: (Tracks.Group, Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trackType == C.TRACK_TYPE_AUDIO) "Select Audio Track" else "Select Subtitles") },
        text = {
            val relevantGroups = tracks.groups.filter { it.type == trackType }
            if (relevantGroups.isEmpty()) Text("No tracks available") else {
                LazyColumn {
                    relevantGroups.forEach { group ->
                        items(group.length) { trackIndex ->
                            val format = group.getTrackFormat(trackIndex)
                            ListItem(
                                headlineContent = { Text("${format.language ?: "Unknown"} (${format.bitrate / 1000} kbps)") },
                                modifier = Modifier.clickable { onTrackSelected(group, trackIndex) },
                                trailingContent = { if (group.isTrackSelected(trackIndex)) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(settings: PlayerSettings, onSettingsChanged: (PlayerSettings) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Hardware Acceleration", fontWeight = FontWeight.Bold)
                Text("Prefer hardware decoders", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Switch(checked = settings.useHardwareAcceleration, onCheckedChange = { onSettingsChanged(settings.copy(useHardwareAcceleration = it)) })
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("High Performance Buffer", fontWeight = FontWeight.Bold)
                Text("Better for 1080p/4K MKV", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Switch(checked = settings.highPerformanceMode, onCheckedChange = { onSettingsChanged(settings.copy(highPerformanceMode = it)) })
        }
        Spacer(Modifier.height(32.dp))
        Text("Resize Mode", fontWeight = FontWeight.Bold)
        listOf("Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL, "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM).forEach { (name, mode) ->
            Row(Modifier.fillMaxWidth().height(48.dp).selectable(selected = (settings.resizeMode == mode), onClick = { onSettingsChanged(settings.copy(resizeMode = mode)) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = (settings.resizeMode == mode), onClick = null)
                Text(name, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun UrlInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Play from URL") }, text = { TextField(value = text, onValueChange = { text = it }, placeholder = { Text("https://example.com/video.mp4" ) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Play") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun YpvMenuButtons(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, containerColor: Color = MaterialTheme.colorScheme.primary, onBtnClick: () -> Unit) {
    Button(onClick = onBtnClick, colors = ButtonDefaults.buttonColors(containerColor = containerColor), modifier = Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(12.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MainApp() {
    var playingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var playerSettings by remember { mutableStateOf(PlayerSettings()) }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { playingUrl = it.toString() } }

    if (playingUrl != null) VideoPlayer(playingUrl!!, playerSettings, onBack = { playingUrl = null })
    else if (showSettings) SettingsScreen(playerSettings, { playerSettings = it }, { showSettings = false })
    else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.gemini_generated_image_84brzp84brzp84br), null, contentScale = ContentScale.Crop, modifier = Modifier.size(128.dp).clip(RoundedCornerShape(16.dp)))
            Text("YPV Media Player Beta 1.1", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF32CD32))
            Spacer(Modifier.height(25.dp))
            YpvMenuButtons("Open Local Video", Icons.Default.PlayArrow, Color(0xFF32CD32)) { videoPickerLauncher.launch("video/*") }
            Spacer(Modifier.height(25.dp))
            YpvMenuButtons("Open URL", Icons.Default.Add, Color(0xFF32CD32)) { showUrlDialog = true }
            Spacer(Modifier.height(25.dp))
            YpvMenuButtons("Settings", Icons.Default.Settings, Color(0xFF32CD32)) { showSettings = true }
            Spacer(Modifier.height(25.dp))
            Text("Developed by Yassien Elhariry (Yassogame67)", fontSize = 10.sp)
        }
    }
    if (showUrlDialog) UrlInputDialog({ showUrlDialog = false }, { playingUrl = it; showUrlDialog = false })
}
