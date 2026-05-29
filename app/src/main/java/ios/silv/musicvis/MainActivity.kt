package ios.silv.musicvis

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.core.graphics.toColor
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
import java.io.File
import kotlin.math.sqrt


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
                Output()
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
                        val (_, out, m) = state
                        if (m == 0) return@Column

                        val animatedValues = remember {
                            List(m) { Animatable(out[it]) }
                        }

                        LaunchedEffect(out) {
                            for (i in 0..<m) {
                                launch {
                                    animatedValues[i].animateTo(
                                        out[i],
                                        animationSpec = tween(durationMillis = 30, easing = LinearEasing)
                                    )
                                }
                            }
                        }

                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.6f)
                        ) {
                            visualizer2(animatedValues)
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

fun DrawScope.visualizer2(
    values:  List<Animatable<Float, AnimationVector1D>>
) {
    drawRect(Color.DarkGray, size = size)
    val w = size.width
    val h = size.height
    val m = values.size

    // The width of a single bar
    val cell_width = w / m;

    // Global color parameters
    val saturation = 0.75f;
    val value = 1.0f;

    // Display the Bars
    for (i in 0..<m) {
        val t = values[i].value
        val hue = i.toFloat() / m

        val startX = i * cell_width
        val startY = h * t

        val endX = i * cell_width
        val endY = h

        val thick = cell_width

        drawLine(
            color = Color.hsv(hue * 360, saturation, value),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = thick
        )
    }
}