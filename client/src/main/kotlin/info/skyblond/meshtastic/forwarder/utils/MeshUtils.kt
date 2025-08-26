package info.skyblond.meshtastic.forwarder.utils

fun sliceMessage(text: String): Sequence<String> = sequence {
    var str = text
    while (str.isNotEmpty()) {
        val subLength =
            (1..str.length).findLast { str.take(it).toByteArray().size <= 200 } ?: 1
        val text = str.take(subLength)
        str = str.drop(subLength)
        yield(text)
    }
}