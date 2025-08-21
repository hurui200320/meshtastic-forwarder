package info.skyblond.meshtastic.forwarder.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter


class TokenAuthFilter(
    private val roTokens: Set<String>,
    private val rwTokens: Set<String>
) : OncePerRequestFilter() {
    private val roAuthorities by lazy {
        SecurityConfig.READ_ONLY_AUTHORITY_SET.map { SimpleGrantedAuthority(it) }
    }
    private val rwAuthorities by lazy {
        SecurityConfig.READ_WRITE_AUTHORITY_SET.map { SimpleGrantedAuthority(it) }
    }

    init {
        logger.info(
            "Initialized TokenAuthFilter with " +
                    "${roTokens.size} read only tokens " +
                    "and ${rwTokens.size} read write tokens"
        )
    }

    private fun auth(
        token: String,
        tokenSet: Set<String>,
        authorities: List<SimpleGrantedAuthority>
    ): Authentication? {
        if (tokenSet.contains(token)) {
            return UsernamePasswordAuthenticationToken(
                token, token, authorities
            )
        }
        return null
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            // Extract token from the header
            val token = header.substring(7)

            // get authentication
            val authentication = auth(token, rwTokens, rwAuthorities)
                ?: auth(token, roTokens, roAuthorities)
            // save if not null
            authentication?.let {
                // save to normal context
                SecurityContextHolder.getContext().authentication = it
            }

        }
        chain.doFilter(request, response) // Continue filter chain
    }
}