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
import logcat.logcat
import java.nio.ByteBuffer


@OptIn(UnstableApi::class)
class VisualizerAudioSink(
    context: Context
) : ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {

    val renderSnapshot = MutableStateFlow(intArrayOf() to 0)

    private var channelCount: Int = NO_VALUE
    private var sampleRate: Int = NO_VALUE
    private var encoding: Int = NO_VALUE
    private var bytesPerSample: Int = 0

    private val globalFrames1 = IntArray(2048)
    private val globalFrames2 = IntArray(2048)

    private var currentBuffer = globalFrames1
    private var nextBuffer = globalFrames2

    private var globalFramesCount = 0

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

        logcat { frameSizeInBytes.toString() }

        val ib = buffer.asIntBuffer()
        ib.get(currentBuffer, 0, frames)

        globalFramesCount = frames

        renderSnapshot.value = currentBuffer to globalFramesCount

        currentBuffer = nextBuffer.also {
            nextBuffer = currentBuffer
        }

        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }
}
