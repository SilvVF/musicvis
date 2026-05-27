package ios.silv.musicvis

import kotlin.math.*
import org.jetbrains.kotlinx.multik.ndarray.complex.*

// https://github.com/jnalon/fast-fourier-transform/blob/master/kotlin/fft.kt
object FFT {

    val pi: Float = atan2(1f, 1f) * 4

    fun cexp(theta: Float) = ComplexFloat(
        cos(theta),
        sin(theta)
    )

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

    fun iterativeFFT(x: FloatArray): ComplexFloatArray
    {
        val N = x.size
        val X = ComplexFloatArray(N)

        val r = (round(log(N.toFloat(), 2.0f))).toInt()    // Number of bits;
        for (k in 0..N-1) {
            var l = bitReverse(k, r)                       // Reorder the vector according to
            X[l] = x[k].i                                 //   the bit-reversed order;
        }

        var step: Int = 1                                  // Computation of twiddle factors;
        for (k in 1..r) {
            var l = 0
            while (l < N) {
                var W = cexp(-pi/step)          // Twiddle factors;
                var Wkn = ComplexFloat(1.0f, 0.0f)
                for (n in 0..step-1) {
                    var p = l + n
                    var q = p + step
                    X[q] = X[p] - Wkn * X[q]               // Recombine results;
                    X[p] = X[p]*2.0f - X[q]
                    Wkn = Wkn * W                          // Update twiddle factors;
                }
                l = l + 2*step
            }
            step = step * 2
        }

        return X                                           // Return value;
    }
}