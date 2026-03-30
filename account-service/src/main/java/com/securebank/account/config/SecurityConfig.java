package com.securebank.account.config;

import com.securebank.common.security.JwtAuthFilter;
import com.securebank.common.security.JwtService;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Bean
    public JwtService jwtService() {
        // accessTokenExpiryMs is not used for validation-only, but JwtService requires it
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

                        // Create account — TELLER and above
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAnyRole("TELLER", "MANAGER", "ADMIN")

                        // Update account — TELLER and above
                        .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/**").hasAnyRole("TELLER", "MANAGER", "ADMIN")

                        // Close account — MANAGER and above (irreversible operation)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/**").hasAnyRole("MANAGER", "ADMIN")

                        // All other endpoints (GET) — any authenticated user
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
