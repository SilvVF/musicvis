package ios.silv.musicvis

import kotlin.math.*
import org.jetbrains.kotlinx.multik.ndarray.complex.*

// https://github.com/jnalon/fast-fourier-transform/blob/master/kotlin/fft.kt
object FFT {

    val pi: Float = atan2(1f, 1f) * 4

    fun pick(inp: FloatArray, size: Int): ComplexFloatArray {
        return if (isPowerOfTwo(size)) iterativeFFT(inp, size) else bluesteinFFT(inp, size)
    }

    fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }

    fun cexp(theta: Float) = ComplexFloat(
        cos(theta),
        sin(theta)
    )

    fun bluesteinFFT(x: FloatArray, size: Int): ComplexFloatArray {
        val M = nextPowerOfTwo(2 * size - 1)  // Pad to next power of 2

        val padded = FloatArray(M)
        for (i in 0..<size) {
            padded[i] = x[i]
        }

        val chirp = FloatArray(M)
        for (i in 0..<size) {
            val angle = -pi * i * i / size
            chirp[i] = cos(angle)
        }

        val filtered = FloatArray(M)
        for (i in 0..<size) {
            filtered[i] = padded[i] * chirp[i]
        }

        val fft = iterativeFFT(filtered, filtered.size)

        val output = ComplexFloatArray(size)
        for (i in 0..<size) {
            val angle = pi * i * i / size
            val invChirp = cexp(angle)
            output[i] = fft[i] * invChirp / M
        }

        return output
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var power = 1
        while (power < n) power *= 2
        return power
    }

    fun bitReverse(k: Int, r: Int): Int
    {
        var l: Int = 0                                     // Accumulate the results;
        var k0: Int = k
        for (i in 1..r) {                                  // Loop on every bit;
            l = (l shl 1) + (k0 and 1)                     // Test less signficant bit and add;
            k0 = (k0 shr 1)                                // Test next bit;
        }
        return l;
    }

    fun iterativeFFT(x: FloatArray, size: Int): ComplexFloatArray
    {
        val output = ComplexFloatArray(size)

        val r = (round(log(size.toFloat(), 2.0f))).toInt()    // Number of bits;
        for (k in 0..<size) {
            val l = bitReverse(k, r)                       // Reorder the vector according to
            output[l] = x[k].i                                 //   the bit-reversed order;
        }

        var step: Int = 1                                  // Computation of twiddle factors;
        for (k in 1..r) {
            var l = 0
            while (l < size) {
                val W = cexp(-pi/step)          // Twiddle factors;
                var Wkn = ComplexFloat(1.0f, 0.0f)
                for (n in 0..<step) {
                    val p = l + n
                    val q = p + step
                    output[q] = output[p] - Wkn * output[q]               // Recombine results;
                    output[p] = output[p]*2.0f - output[q]
                    Wkn *= W                          // Update twiddle factors;
                }
                l += 2*step
            }
            step *= 2
        }

        return output                                           // Return value;
    }
}