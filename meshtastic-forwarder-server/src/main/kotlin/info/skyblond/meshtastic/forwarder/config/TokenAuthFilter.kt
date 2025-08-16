package info.skyblond.meshtastic.forwarder.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter


class TokenAuthFilter(
    private val tokens: Map<String, List<String>>
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        logger.info(
            "Incoming from ${request.remoteAddr}:${request.remotePort} for request ${request.requestURI}"
        )

        if (header != null && header.startsWith("Bearer ")) {
            // Extract token from the header
            val token = header.substring(7)

            if (tokens.containsKey(token)) {
                // Set authentication
                val authentication = UsernamePasswordAuthenticationToken(
                    token, token,
                    tokens[token]!!.map { SimpleGrantedAuthority(it) }
                )
                logger.info(
                    "Authenticated ${request.remoteAddr}:${request.remotePort} " +
                            "for request ${request.requestURI}" +
                            " with token: '$token', authorities: ${authentication.authorities}"
                )
                // save to normal context
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        chain.doFilter(request, response) // Continue filter chain
    }
}