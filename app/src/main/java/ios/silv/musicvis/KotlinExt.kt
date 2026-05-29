package ios.silv.musicvis

@JvmName("sumOfFloat")
public inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum: Float = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

inline fun <T> loop(
    initial: T,
    crossinline condition: (T) -> Boolean,
    crossinline iteration: (T) -> T,
    crossinline body: (T) -> Unit
) {
    var i = initial
    while (condition(i)) {
        body(i)
        i = iteration(i)
    }
}
