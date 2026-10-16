package com.jonhararagi.archivero

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerActivity : ComponentActivity() {
    private var videoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val initialUri = intent.data
        val initialName = intent.getStringExtra("displayName") ?: initialUri?.lastPathSegment ?: "Vídeo"
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        videoPlayer = player
        if (initialUri != null) {
            player.setMediaItem(MediaItem.Builder().setUri(initialUri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(initialName).build()).build())
            player.prepare()
            player.playWhenReady = true
        }
        setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    player = player,
                    initialUri = initialUri,
                    initialName = initialName,
                    onSelectVideo = { uri, name ->
                        player.setMediaItem(MediaItem.Builder().setUri(uri)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build()).build())
                        player.prepare()
                        player.playWhenReady = true
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        videoPlayer?.release()
        videoPlayer = null
        super.onDestroy()
    }
}

@Composable
private fun VideoPlayerScreen(
    player: ExoPlayer,
    initialUri: Uri?,
    initialName: String,
    onSelectVideo: (Uri, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activePlayer = player
    var title by remember(initialUri, initialName) { mutableStateOf(initialName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            title = readableDisplayName(context, uri)
            errorMessage = null
            onSelectVideo(uri, title)
        }
    }

    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.errorCodeName + ": " +
                    (error.message ?: "El formato o códec no es compatible con este dispositivo.")
            }
        }
        activePlayer.addListener(listener)
        onDispose { activePlayer.removeListener(listener) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF15171B)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver a Archivero", tint = Color.White)
            }
            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
            Text(
                title,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                maxLines = 1,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = true; player = activePlayer } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            update = { view -> if (view.player !== activePlayer) view.player = activePlayer }
        )

        if (errorMessage != null) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF211D1D)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No se pudo reproducir el vídeo", color = Color(0xFFFFB4AB), style = MaterialTheme.typography.titleSmall)
                Text(errorMessage!!, color = Color.White, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pickVideo.launch(arrayOf("video/*")) }) { Text("Elegir otro vídeo") }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF15171B)).padding(10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                    Text("Abrir otro vídeo", color = Color.White)
                }
                TextButton(onClick = onBack) { Text("Volver a Archivero", color = Color.White) }
            }
        }
    }
}

private fun readableDisplayName(context: Context, uri: Uri): String {
    val result = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return result?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        ?: "Vídeo"
}
