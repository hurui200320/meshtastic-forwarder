package info.skyblond.meshtastic.forwarder.lib.http

import retrofit2.Response
import retrofit2.http.*

interface DeviceInfoService {

    /**
     * List of base64 encoded [build.buf.gen.meshtastic.Channel].
     * */
    @GET("/device/channels")
    suspend fun getChannels(): List<String>

    /**
     * Map from [build.buf.gen.meshtastic.Config.PayloadVariantCase.getNumber] to
     * base64 encoded [build.buf.gen.meshtastic.Config].
     * */
    @GET("/device/configs")
    suspend fun getConfigs(): Map<Int, String>

    /**
     * Base64 encoded [build.buf.gen.meshtastic.MyNodeInfo].
     * */
    @GET("/device/myNodeInfo")
    suspend fun getMyNodeInfo(): String

    /**
     * Generate a unique packet ID that has not been used yet.
     * */
    @GET("/device/generateNewPacketId")
    suspend fun generateNewPacketId(): Int

    /**
     * A set of node numbers.
     * */
    @GET("/device/nodes")
    suspend fun getNodes(): Set<Int>

    /**
     * Return base64 encoded [build.buf.gen.meshtastic.NodeInfo] for the given node number.
     * Or null if the node is not known.
     * */
    @GET("/device/nodes/{num}")
    suspend fun getNode(
        @Path("num") nodeNum: Int
    ): String?
}

interface SendMessageService {
    @FormUrlEncoded
    @POST("/send/meshPacket")
    suspend fun sendMeshPacket(
        @Field("meshPacket") meshPacket: ByteArray,
        @Field("autoEncrypt") autoEncrypt: Boolean = true,
        @Field("autoLoraHop") autoLoraHop: Boolean = true,
        @Field("timeout") timeout: Long = 120,
    ): Response<String>
}