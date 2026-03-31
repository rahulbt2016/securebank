package com.securebank.account.config;

import com.securebank.common.security.JwtAuthFilter;
import com.securebank.common.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Bean
    public JwtService jwtService() {
        return new JwtService(jwtSecret, accessTokenExpiryMs);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtService());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Infrastructure / docs — no token needed
                        .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()

                        // Create account — any authenticated user
                        // CUSTOMER: customerId forced from JWT (own accounts only)
                        // STAFF: customerId required in request body
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").authenticated()

                        // Update account — TELLER and above
                        .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/**").hasAnyRole("TELLER", "MANAGER", "ADMIN")

                        // Close account — MANAGER and above (irreversible operation)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/**").hasAnyRole("MANAGER", "ADMIN")

                        // All other endpoints (GET) — any authenticated user
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeJsonError(res, 401, "UNAUTHORIZED", "Authentication required — provide a valid Bearer token"))
                        .accessDeniedHandler((req, res, e) ->
                                writeJsonError(res, 403, "FORBIDDEN", "You do not have permission to perform this action"))
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeJsonError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"success\":false,\"error\":{\"status\":%d,\"code\":\"%s\",\"message\":\"%s\"},\"timestamp\":\"%s\"}",
                status, code, message, Instant.now()));
    }
}
