package info.skyblond.meshtastic.forwarder.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository


@Configuration
@EnableConfigurationProperties(MeshtasticForwarderConfigProperties::class)
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun authenticationManager(): AuthenticationManager {
        return AuthenticationManager {
            throw AuthenticationServiceException("Authentication is not supported")
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        mfsConfig: MeshtasticForwarderConfigProperties,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext {
                // here we explicitly specify the security context repository.
                // by default, we use http session, but we don't have a session,
                // and the request attr is the only way for DeferredResult to work
                // with CompletableFuture, which the result is set by another thread.
                it.securityContextRepository(RequestAttributeSecurityContextRepository())
            }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/ws/packet").hasAuthority(AUTHORITY_READ_MESH_PACKET)
                    .requestMatchers("/device/**").hasAuthority(AUTHORITY_READ_DEVICE_INFO)
                    .requestMatchers("/send/meshPacket").hasAuthority(AUTHORITY_SEND_MESH_PACKET)
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                TokenAuthFilter(
                    roTokens = tokensFromCsv(mfsConfig.roTokens),
                    rwTokens = tokensFromCsv(mfsConfig.rwTokens),
                ), BasicAuthenticationFilter::class.java
            )
            .build()

    private fun tokensFromCsv(csv: String): Set<String> =
        csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    companion object {

        const val AUTHORITY_READ_MESH_PACKET = "READ_MESH_PACKET"
        const val AUTHORITY_READ_DEVICE_INFO = "READ_DEVICE_INFO"

        const val AUTHORITY_SEND_MESH_PACKET = "SEND_MESH_PACKET"

        val READ_ONLY_AUTHORITY_SET = setOf(AUTHORITY_READ_MESH_PACKET, AUTHORITY_READ_DEVICE_INFO)
        val READ_WRITE_AUTHORITY_SET = READ_ONLY_AUTHORITY_SET + AUTHORITY_SEND_MESH_PACKET
    }
}