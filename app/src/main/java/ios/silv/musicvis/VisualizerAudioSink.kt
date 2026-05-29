package ios.silv.musicvis

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import logcat.logcat
import org.jetbrains.kotlinx.multik.ndarray.complex.ComplexFloat
import org.jetbrains.kotlinx.multik.ndarray.complex.ComplexFloatArray
import java.nio.Buffer
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.ln


private const val N = 1 shl 10

data class Output(
    val out: ComplexFloatArray = ComplexFloatArray(),
    val frames: Int = 0,
)

// magnitude of the vector formed by complex number
// https://youtu.be/ivLIov6ta-8?list=PLpM-Dvs8t0Vak1rrE2NJn8XYEJ5M7-BqT&t=1103
fun amp(c: ComplexFloat): Float {
    val a = c.re
    val b = c.im
    return ln(a * a + b * b)
}

private val fftCoroutineContext = newSingleThreadContext("FFT_Worker")

@OptIn(UnstableApi::class)
class VisualizerAudioSink(
    context: Context
) : ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {

    val fftOutput = MutableSharedFlow<Output>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 1
    )

    private var fftJob: Job? = null

    private var channelCount: Int = NO_VALUE
    private var sampleRate: Int = NO_VALUE
    private var encoding: Int = NO_VALUE

    private val inp = FloatArray(N)
    private val inp2 = FloatArray(N)

    var skipNext = false

    private var scope = CoroutineScope(fftCoroutineContext + Job())


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
        require(
            encoding in arrayOf(
                C.ENCODING_PCM_8BIT,
                C.ENCODING_PCM_16BIT,
                C.ENCODING_PCM_24BIT,
                C.ENCODING_PCM_32BIT,
                C.ENCODING_PCM_FLOAT
            )
        )

        inp.fill(0f)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun play() {
        scope = CoroutineScope(fftCoroutineContext + Job())
        super.play()
    }

    override fun pause() {
        scope.cancel()
        super.pause()
    }

    override fun handleDiscontinuity() {
        inp.fill(0f)
        skipNext = false
        scope.cancel()
        scope = CoroutineScope(fftCoroutineContext + Job())
        super.handleDiscontinuity()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!skipNext) {
            buffer.mark()

            when (encoding) {
                C.ENCODING_PCM_16BIT -> decodePcm16Bit(buffer)
                C.ENCODING_PCM_8BIT -> decodePcm8Bit(buffer)
                C.ENCODING_PCM_24BIT -> decodePcm24BitPacked(buffer)
                C.ENCODING_PCM_32BIT -> decodePcm32Bit(buffer)
                C.ENCODING_PCM_FLOAT -> decodePcm32BitFloat(buffer)
                else -> error("unsupported encoding")
            }

            buffer.reset()
            // https://youtu.be/ivLIov6ta-8?list=PLpM-Dvs8t0Vak1rrE2NJn8XYEJ5M7-BqT
            // https://www.youtube.com/redirect?event=video_description&redir_token=QUFFLUhqbTRsZ1d4RFFNclRGRnFXWHFwZk1fRC1DSkVrZ3xBQ3Jtc0ttdEF4LUpqU1hGTjREU0hxWFJ6Y3ZtUUcxa3VmTDZmRW9kcTItcHh3ajJzdmt0VVl5blAxdGVyMURmMlk2QlBtNGRvbG9LLWEzZ2RnMjFDYWZZSTVWVnZQRTFoMVRaRXFZcHR2UFliMVlzQ1RucVE0SQ&q=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FHann_function&v=ivLIov6ta-8

            if (fftJob?.isActive?.not() ?: true) {

                for (i in 0..inp.lastIndex) {
                    val t = i.toFloat() / inp.size
                    val hann = 0.5f - 0.5f * cos(2f * FFT.pi * t)
                    inp2[i] = inp[i] * hann
                }

                fftJob = scope.launch {
                    val out = FFT.pick(inp2, inp2.size)
                    fftOutput.emit(Output(out, out.size))
                }
            }
        }

        val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        skipNext = !handled

        return handled
    }

    private inline fun pushFramesToInput(source: Buffer, crossinline get: () -> Float) {
        if (source.remaining() > inp.size) {
            val overflow = source.remaining() - inp.size
            repeat(overflow) {
                get()
            }
        }

        inp.copyInto(
            destination = inp,
            destinationOffset = 0,
            startIndex = inp.size - source.remaining(),
            endIndex = inp.size
        )
        var i = inp.size - source.remaining()
        while (source.hasRemaining()) {
            inp[i++] = get()
        }
    }

    private fun decodePcm16Bit(buffer: ByteBuffer) {
        val ib = buffer.asIntBuffer()
        pushFramesToInput(source = ib) {
            val sample = ib.get()
            val left = (sample shr 16).toShort()
            left.toFloat()
        }
    }


    // PCM 8-bit (unsigned, stereo - left channel only)
    private fun decodePcm8Bit(buffer: ByteBuffer) {
        pushFramesToInput(source = buffer) {
            val left = buffer.get().toInt() and 0xFF
            buffer.get().toInt() and 0xFF  // Skip right channel
            left.toFloat()
        }
    }

    // PCM 24-bit (signed, packed 3 bytes, stereo - left channel only)
    private fun decodePcm24BitPacked(buffer: ByteBuffer) {
        pushFramesToInput(source = buffer) {
            // Read left channel (3 bytes)
            val byte1 = buffer.get().toInt() and 0xFF
            val byte2 = buffer.get().toInt() and 0xFF
            val byte3 = buffer.get().toInt() and 0xFF

            // Combine bytes (little-endian)
            val sample24 = (byte3 shl 16) or (byte2 shl 8) or byte1

            // Sign-extend from 24-bit to 32-bit
            val left = if ((sample24 and 0x800000) != 0) {
                sample24 or 0xFF000000.toInt()
            } else {
                sample24
            }

            // Skip right channel (3 bytes)
            buffer.get()
            buffer.get()
            buffer.get()

            left.toFloat()
        }
    }

    // PCM 32-bit (signed, stereo - left channel only)
    private fun decodePcm32Bit(buffer: ByteBuffer) {
        val ib = buffer.asIntBuffer()
        pushFramesToInput(source = ib) {
            val left = ib.get()
            ib.get()  // Skip right channel
            left.toFloat()
        }
    }

    // PCM 32-bit Float (stereo - left channel only)
    private fun decodePcm32BitFloat(buffer: ByteBuffer) {
        val fb = buffer.asFloatBuffer()
        pushFramesToInput(source = fb) {
            val left = fb.get()
            fb.get()  // Skip right channel
            left
        }
    }

}
