package info.skyblond.meshtastic.forwarder.client

import build.buf.gen.meshtastic.MeshPacket
import info.skyblond.meshtastic.forwarder.client.http.DeviceInfoService
import info.skyblond.meshtastic.forwarder.client.http.MFHttpClient
import info.skyblond.meshtastic.forwarder.client.http.SendMessageService
import info.skyblond.meshtastic.forwarder.client.ws.MFWebSocketClient
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.concurrent.TimeUnit

class MeshtasticForwarderClient(
    okhttpClient: OkHttpClient,
    serverBaseUrl: String,
    enableTls: Boolean,
    token: String,
) : AutoCloseable {
    private val okhttp = okhttpClient.newBuilder()
        // ping interval for ws, this also acts as an idle timeout
        // (if the server doesn't respond in this interval, the connection will be closed)
        .pingInterval(5, TimeUnit.SECONDS)
        // a general timeout for http,
        // we will have a longer wait time on sending packets
        .readTimeout(10, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            )
        }
        .build()

    // =============== websocket ===============
    private val wsClient: MFWebSocketClient = MFWebSocketClient(okhttp, serverBaseUrl, enableTls)

    /**
     * @see MFWebSocketClient.meshPacketChannel
     * */
    val meshPacketChannel: ReceiveChannel<MeshPacket> = wsClient.meshPacketChannel

    // =============== http ===============
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("${if (enableTls) "https" else "http"}://$serverBaseUrl")
        .addConverterFactory(JacksonConverterFactory.create())
        .client(okhttp)
        .build()

    /**
     * Client for HTTP endpoints
     * */
    val httpService: MFHttpClient

    init {
        val deviceInfoService = retrofit.create(DeviceInfoService::class.java)
        val sendMessageService = retrofit.create(SendMessageService::class.java)
        httpService = MFHttpClient(
            deviceInfoService = deviceInfoService,
            sendMessageService = sendMessageService
        )
    }

    override fun close() {
        // close subcomponents first
        wsClient.close()
        // close our own resources
    }
}