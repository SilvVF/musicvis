package ios.silv.musicvis

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import logcat.LogPriority
import logcat.logcat
import org.jetbrains.kotlinx.multik.ndarray.complex.ComplexFloat
import org.jetbrains.kotlinx.multik.ndarray.complex.ComplexFloatArray
import org.jetbrains.kotlinx.multik.ndarray.complex.maxOf
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

private const val N = 256

data class Output(
    val out: ComplexFloatArray,
    val frames: Int
) {
    val maxAmp = if (out.size > 0) out.maxOf { amp(it) } else 0f
}

fun amp(c: ComplexFloat): Float {
    return max(abs(c.im), abs(c.re))
}

@OptIn(UnstableApi::class)
class VisualizerAudioSink(
    context: Context
) : ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {

    val renderSnapshot = MutableStateFlow(Output(ComplexFloatArray(), 0))

    private var channelCount: Int = NO_VALUE
    private var sampleRate: Int = NO_VALUE
    private var encoding: Int = NO_VALUE
    private var bytesPerSample: Int = 0

    private val inp = FloatArray(N)
    private val frameBuff = IntArray(2048)

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        channelCount = inputFormat.channelCount
        sampleRate = inputFormat.sampleRate
        encoding = inputFormat.pcmEncoding

        logcat { "channelCount: $channelCount, sampleRate: $sampleRate, encoding: $encoding" }

        require(channelCount != NO_VALUE)
        require(sampleRate != NO_VALUE)

        bytesPerSample = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            else -> 2
        }

        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {

        val frameSizeInBytes = channelCount * bytesPerSample
        val frames = buffer.remaining() / frameSizeInBytes

        if (frames < N) {
            logcat(LogPriority.ERROR) { "not enough frames in buffer" }
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        buffer.mark()
        val ib = buffer.asIntBuffer()
        ib.get(frameBuff, 0, minOf(frames, frameBuff.size))
        buffer.reset()

        for (i in 0..<N) {
            val sample = frameBuff[i]
            val left = (sample shr 16).toShort()
            inp[i] = left.toFloat()
        }

        val out = FFT.iterativeFFT(inp)

        renderSnapshot.value = Output(out, N)

        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }
}
