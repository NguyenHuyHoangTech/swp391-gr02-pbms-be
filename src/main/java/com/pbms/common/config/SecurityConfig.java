package com.pbms.common.config;

import com.pbms.common.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 1. PUBLIC & AUTHENTICATION
                .requestMatchers("/api/v1/public/**", "/api/v1/auth/**", "/api/v1/webhooks/**", "/api/v1/iot/**").permitAll()
                .requestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/ws/**", "/ws-pbms/**", "/uploads/**").permitAll()
                
                // 2. SYSTEM ADMIN
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                
                // 3. MANAGER DASHBOARD
                .requestMatchers("/api/v1/manager/**", "/api/v1/reports/**").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers("/api/v1/dashboard/**").hasAnyRole("STAFF", "MANAGER", "ADMIN")
                
                // 4. STAFF POS & OPERATIONS
                .requestMatchers("/api/v1/gates/**", "/api/v1/work-sessions/**").hasAnyRole("STAFF", "MANAGER", "ADMIN")
                .requestMatchers("/api/v1/payments/**").hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                .requestMatchers("/api/v1/operation/monthly-tickets/**").hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                .requestMatchers("/api/v1/operation/**").hasAnyRole("STAFF", "MANAGER", "ADMIN")
                .requestMatchers("/api/v1/incidents/**").hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                .requestMatchers("/api/v1/parking-sessions/**").hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                .requestMatchers("/api/v1/infrastructure/**").hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                
                // 5. CUSTOMER PORTAL
                .requestMatchers("/api/v1/customer/**").hasAnyRole("CUSTOMER", "MANAGER", "ADMIN")
                .requestMatchers("/api/v1/user/**").hasRole("CUSTOMER")
                
                // FALLBACK
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable())); // for h2-console
        
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}

