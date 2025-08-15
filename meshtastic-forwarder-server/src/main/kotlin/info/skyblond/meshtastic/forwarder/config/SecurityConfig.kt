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
            .authorizeHttpRequests {
                it
                    .requestMatchers("/ws/packet").hasAuthority("READ_MESH_PACKET")
                    .requestMatchers("/device/**").hasAuthority("READ_DEVICE_INFO")
                    .requestMatchers("/send/meshPacket").hasAuthority("SEND_MESH_PACKET")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                TokenAuthFilter(
                    mfsConfig.tokens
                ), BasicAuthenticationFilter::class.java
            )
            .build()

}