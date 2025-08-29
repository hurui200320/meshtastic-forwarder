package info.skyblond.meshtastic.forwarder.utils

import build.buf.gen.meshtastic.MeshPacket
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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

fun MeshPacket.getRxZonedDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
): ZonedDateTime {
    val rxTimestamp =
        if (rxTime == 0) System.currentTimeMillis() / 1000
        else rxTime.toLong()
    return ZonedDateTime.ofInstant(Instant.ofEpochSecond(rxTimestamp), zoneId)
}