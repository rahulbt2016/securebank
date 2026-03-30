package com.securebank.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Intercepts every request and, if a valid Bearer token is present, populates
 * the Spring Security context so that downstream security rules can check roles.
 *
 * Flow:
 *   1. Extract "Authorization: Bearer <token>" header.
 *   2. Validate the token with JwtService.
 *   3. Set a UsernamePasswordAuthenticationToken in the SecurityContext so
 *      Spring Security treats the request as authenticated.
 *   4. Pass the request along the filter chain.
 *
 * If no token is present, or if the token is invalid/expired, the filter does
 * nothing — the SecurityContext remains unauthenticated and Spring Security
 * will enforce access rules as configured in each service's SecurityConfig.
 *
 * Why not @Component?
 * Registering this as a @Component would cause Spring Boot to add it as a
 * servlet filter AND as a security filter — doubling up every request. Instead,
 * each service's SecurityConfig instantiates and registers it explicitly via
 * http.addFilterBefore().
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (jwtService.isTokenValid(token) &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            UUID userId = jwtService.extractUserId(token);
            String role  = jwtService.extractRole(token);

            // Spring Security's hasRole("TELLER") checks for authority "ROLE_TELLER"
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        chain.doFilter(request, response);
    }
}
