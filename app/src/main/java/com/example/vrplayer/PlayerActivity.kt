package com.example.vrplayer

import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var tracker: HeadTracker
    private lateinit var renderer: VrRenderer
    private lateinit var glView: GLSurfaceView

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = windowManager.defaultDisplay.rotation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) { finish(); return }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Błąd odtwarzania: ${error.errorCodeName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
            prepare()
            playWhenReady = true
        }

        tracker = HeadTracker(this) { displayRotation() }
        if (!tracker.available) {
            Toast.makeText(this, "Brak żyroskopu – ruch głowy nie będzie działał", Toast.LENGTH_LONG).show()
        }

        renderer = VrRenderer(tracker) { surface ->
            runOnUiThread { player.setVideoSurface(surface) }
        }
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8AA4FF))) {
                PlayerScreen(player, glView, renderer, onExit = { finish() })
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        tracker.start()
        player.play()
    }

    override fun onPause() {
        player.pause()
        tracker.stop()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        player.release()
        renderer.release()
        super.onDestroy()
    }
}

@Composable
private fun PlayerScreen(
    player: Player,
    glView: GLSurfaceView,
    renderer: VrRenderer,
    onExit: () -> Unit
) {
    var controls by remember { mutableStateOf(true) }
    var stereo by remember { mutableStateOf(true) }
    var lens by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    SideEffect {
        renderer.stereo = stereo
        renderer.lensCorrection = lens
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) {
                pos = player.currentPosition
                dur = player.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    // auto-ukrywanie menu
    LaunchedEffect(controls, playing, dragging) {
        if (controls && playing && !dragging) {
            delay(4000)
            controls = false
        }
    }

    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        // warstwa gestów: tap (VR = pauza, zwykły = menu), long-press = menu
        Box(
            Modifier.fillMaxSize().pointerInput(stereo) {
                detectTapGestures(
                    onTap = { if (stereo) togglePlay() else controls = !controls },
                    onLongPress = { controls = !controls }
                )
            }
        )

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .padding(16.dp)
                    .background(Color(0xB3101320), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(if (dragging) (dragValue * dur).toLong() else pos), fontSize = 12.sp)
                    Slider(
                        value = if (dragging) dragValue else if (dur > 0) pos.toFloat() / dur else 0f,
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = {
                            player.seekTo((dragValue * dur).toLong())
                            dragging = false
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(fmt(dur), fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onExit) { Text("✕ Wyjdź") }
                    TextButton(onClick = { renderer.recenter() }) { Text("◎ Wyśrodkuj") }
                    FilledIconButton(onClick = { togglePlay() }) {
                        Text(if (playing) "⏸" else "▶", fontSize = 20.sp)
                    }
                    FilterChip(
                        selected = stereo,
                        onClick = { stereo = !stereo },
                        label = { Text("VR") }
                    )
                    FilterChip(
                        selected = lens,
                        enabled = stereo,
                        onClick = { lens = !lens },
                        label = { Text("Soczewki") }
                    )
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
