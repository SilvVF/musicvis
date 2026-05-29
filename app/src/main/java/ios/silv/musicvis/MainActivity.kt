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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.multik.ndarray.complex.ComplexFloatArray
import org.jetbrains.kotlinx.multik.ndarray.complex.isEmpty
import org.jetbrains.kotlinx.multik.ndarray.complex.take
import java.io.File
import kotlin.math.ceil


class MainActivity : ComponentActivity() {


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val customAudioSink = VisualizerAudioSink(this)

        val renderSnapshot = customAudioSink.fftOutput
            .sample(1)
            .stateIn(
                lifecycleScope,
                SharingStarted.Lazily,
                Output(ComplexFloatArray(), 0)
            )

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

            val state by renderSnapshot.collectAsStateWithLifecycle()

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
                                .fillMaxHeight(0.6f)
                        ) {
                            drawRect(Color.Black, size = size)

                            visualizer2(state)
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

fun DrawScope.visualizer2(state: Output) {

    val (out, frames) = state
    if (out.isEmpty()) return

    val w = size.width
    val h = size.height
    // input to fft is real so ignore second half of output
    val N = frames / 2
    val maxAmp = state.out.take(N).maxOf { amp(it) }
    val step = 1.06f
    val lowf = 1f
    var m = 0

    loop(lowf, { f -> f < N }, { f ->  ceil(f * step) }) {
        m += 1
    }

    val cellWidth = w / m
    m = 0

    loop(lowf, { f -> f < N }, { f -> ceil(f * step) }) { f ->
        val f1 = ceil(f * step)
        var a = 0f
        loop(f, { q -> q < N  && q < f1.toInt() }, { q -> q + 1}) { q ->
            val b = amp(out[q.toInt()])
            a = maxOf(a, b)
        }

        val t = a / maxAmp
        val barHeight = h / 2 * t

        drawRect(
            color = Color.Green,
            topLeft = Offset(
                x = m * cellWidth,
                y = h - barHeight
            ),
            size = Size(
                width = cellWidth,
                height = barHeight
            )
        )
        m += 1
    }
}