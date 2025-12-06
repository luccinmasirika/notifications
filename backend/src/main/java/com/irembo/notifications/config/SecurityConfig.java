package com.irembo.notifications.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Security Configuration.
 *
 * Two authentication mechanisms:
 * 1. API Key (X-API-KEY header) for /api/** endpoints - handled by
 * APIKeyAuthFilter
 * 2. HTTP Basic Auth for /admin/** endpoints - handled by Spring Security
 *
 * Stateless (no sessions, no CSRF).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * CORS allowed origins configuration.
     * Can be overridden via environment variable CORS_ALLOWED_ORIGINS or Spring property cors.allowed-origins.
     * Format: comma-separated list of origins (e.g., "http://localhost:80,https://example.com")
     * Default: localhost origins for development
     */
    @Value("${cors.allowed-origins:http://localhost,http://localhost:80,http://localhost:4200,http://localhost:8080,http://localhost:1310,http://localhost:1015}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Stateless API, no CSRF needed
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/health", "/actuator/**", "/error").permitAll()

                        // Swagger/OpenAPI endpoints
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll()

                        // Admin endpoints require HTTP Basic Auth
                        .requestMatchers("/admin/**").authenticated()

                        // API endpoints use API Key auth (handled by APIKeyAuthFilter)
                        // Spring Security permits them here, but APIKeyAuthFilter will validate
                        .requestMatchers("/api/**").permitAll()

                        // Deny everything else
                        .anyRequest().denyAll())
                // Enable HTTP Basic Auth for admin endpoints
                .httpBasic(basic -> {
                });

        return http.build();
    }

    /**
     * CORS configuration source.
     * Configures allowed origins from environment variable CORS_ALLOWED_ORIGINS or application property.
     * Origins are parsed from a comma-separated string.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse comma-separated origins from configuration
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset",
                "X-Soft-Throttled", "Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Password encoder for admin credentials.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * In-memory user store for admin users.
     * In production, this should be replaced with database-backed user store.
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${admin.username:admin}") String username,
            @Value("${admin.password:admin123}") String password) {

        UserDetails admin = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }
}
