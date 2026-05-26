package ios.silv.musicvis

import android.content.Context
import android.os.Bundle
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.compose.material3.Player
import ios.silv.musicvis.ui.theme.MusicvisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import java.io.File


class MainActivity : ComponentActivity() {


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val customAudioSink = VisualizerAudioSink(this)

        val player = ExoPlayer.Builder(
            this,
            object : DefaultRenderersFactory(this) {
                override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
                ) {
                    super.buildAudioRenderers(
                        context, extensionRendererMode, mediaCodecSelector,
                        enableDecoderFallback, customAudioSink, eventHandler,
                        eventListener, out
                    )
                }
            }
        )

            .build()


        setContent {
            LifecycleStartEffect(Unit) {
                lifecycleScope.launch {
                    val file = withContext(Dispatchers.IO) {
                        File(cacheDir, "ado_kira.mp3").apply {
                            if (!exists()) {
                                outputStream().use { os ->
                                    resources.openRawResource(R.raw.ado_kira).use { ins ->
                                        ins.copyTo(os)
                                    }
                                }
                            }
                        }
                    }

                    if (!player.isPlaying) {
                        val mediaItem = MediaItem.fromUri(file.toUri())
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    }
                }

                onStopOrDispose {
                    player.stop()
                }
            }

            val state by customAudioSink.renderSnapshot.collectAsStateWithLifecycle()
            val (frames, frameCount) = state

            MusicvisTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                        ) {
                            val w = size.width
                            val h = size.height

                            val cellWidth = w / frameCount

                            repeat(frameCount) { i ->
                                val sample = frames[i]
                                val left = (sample shr 16).toShort()
                                if (left > 0) {
                                    val t = left.toFloat() / Short.MAX_VALUE
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = Offset(
                                            x = i * cellWidth,
                                            y = h / 2 - h / 2 * t
                                        ),
                                        size = Size(
                                            width = cellWidth,
                                            height = h / 2 * t
                                        )
                                    )
                                } else {
                                    val t = left.toFloat() / Short.MIN_VALUE
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = Offset(
                                            x = i * cellWidth,
                                            y = h / 2
                                        ),
                                        size = Size(
                                            width = cellWidth,
                                            height = h / 2 * t
                                        )
                                    )
                                }
                            }
                        }
                        Player(
                            modifier = Modifier,
                            player = player
                        )
                    }
                }
            }
        }
    }
}

