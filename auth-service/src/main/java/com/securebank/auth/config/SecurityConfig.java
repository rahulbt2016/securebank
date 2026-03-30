package com.securebank.auth.config;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
        return new JwtService(jwtSecret, accessTokenExpiryMs);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtService());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints are public — no token needed to log in or register
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Infrastructure / docs
                        .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                        // Any other endpoint in this service requires a valid token
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
