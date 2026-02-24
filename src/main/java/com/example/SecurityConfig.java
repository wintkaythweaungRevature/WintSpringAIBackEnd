package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

   @Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            // ✅ OPTIONS request များကို အားလုံးအတွက် permit ပေးရန်
            .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/audio/**").permitAll() 
            .requestMatchers("/api/ai/**").permitAll() 
            .requestMatchers("/error").permitAll()
            .anyRequest().permitAll()
        );
    return http.build();
}

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed Origins သတ်မှတ်ခြင်း (Pattern အစား List နဲ့ သုံးကြည့်ပါ)
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000", 
            "https://www.wintaibot.com", 
            "https://wintaibot.com",
            "https://api.wintaibot.com"
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", 
        "Content-Type", 
        "Accept", 
        "X-Requested-With", 
        "Origin")); // Headers အားလုံးကို လက်ခံရန်
        configuration.setAllowCredentials(true);             // Cookies/Credentials ပါရင် လက်ခံရန်

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    
}