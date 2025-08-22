package info.skyblond.meshtastic.forwarder.lib.http

import retrofit2.Response

class RequestFailedException(
    private val statusCode: Int,
    private val reason: String,
    private val body: String?
) : Exception("Request failed with status code $statusCode ($reason): $body") {
    constructor(response: Response<*>) : this(
        statusCode = response.code(),
        reason = response.message(),
        body = response.errorBody()?.string()
    )
}