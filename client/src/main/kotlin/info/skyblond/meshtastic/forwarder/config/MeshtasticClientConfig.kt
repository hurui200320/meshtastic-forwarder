package info.skyblond.meshtastic.forwarder.config

import info.skyblond.meshtastic.forwarder.lib.MeshtasticForwarderClient
import okhttp3.OkHttpClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MeshtasticClientConfigProperties::class)
class MeshtasticClientConfig {
    @Bean
    fun meshtasticOkHttpClient() = OkHttpClient.Builder()
        .build()

    @Bean
    fun mfClient(
        config: MeshtasticClientConfigProperties,
        meshtasticOkHttpClient: OkHttpClient,
    ) = MeshtasticForwarderClient(
        meshtasticOkHttpClient,
        config.baseUrl,
        config.enableTls,
        config.token,
    )

    @Bean
    fun mfHttpClient(mfForwarderClient: MeshtasticForwarderClient) =
        mfForwarderClient.httpService

}