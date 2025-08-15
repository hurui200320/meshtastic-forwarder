package info.skyblond.meshtastic.forwarder.client

import build.buf.gen.meshtastic.MeshPacket
import info.skyblond.meshtastic.forwarder.client.http.DeviceInfoService
import info.skyblond.meshtastic.forwarder.client.http.MFHttpClient
import info.skyblond.meshtastic.forwarder.client.http.SendMessageService
import info.skyblond.meshtastic.forwarder.client.ws.MFWebSocketClient
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.component.LifeCycle
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import kotlin.concurrent.thread

class MeshtasticForwarderClient(
    serverBaseUrl: String,
    enableTls: Boolean,
    token: String,
) : AutoCloseable {

    // =============== websocket ===============
    private val jettyHttpClient: HttpClient = HttpClient()
    private val wsClient: MFWebSocketClient

    /**
     * @see MFWebSocketClient.meshPacketFlow
     * */
    val meshPacketFlow: Flow<MeshPacket>

    // =============== http ===============
    private val retrofit: Retrofit

    /**
     * Client for HTTP endpoints
     * */
    val httpService: MFHttpClient

    init {
        // websocket
        jettyHttpClient.start()
        wsClient = MFWebSocketClient(jettyHttpClient, serverBaseUrl, enableTls, token)
        meshPacketFlow = wsClient.meshPacketFlow
        // http
        retrofit = Retrofit.Builder()
            .baseUrl("${if (enableTls) "https" else "http"}://$serverBaseUrl")
            .addConverterFactory(JacksonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                        )
                    }
                    .build()
            )
            .build()
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
        thread { LifeCycle.stop(jettyHttpClient) }
        // close our own resources
    }
}