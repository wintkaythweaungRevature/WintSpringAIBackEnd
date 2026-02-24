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

import org.springframework.http.HttpMethod; // ✅ ဒီ import ပါတာ သေချာပါစေ

@Configuration
@EnableWebSecurity
public class SecurityConfig {
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            // ၁။ OPTIONS (Preflight) requests တွေကို အရင်ခွင့်ပြုမယ်
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            
            // ၂။ API paths တွေကို ခွင့်ပြုမယ်
            .requestMatchers("/api/ai/**").permitAll() 
            .requestMatchers("/api/audio/**").permitAll() 
            .requestMatchers("/error").permitAll()
            
            // ၃။ ကျန်တာတွေကိုလည်း လက်ရှိမှာ စမ်းသပ်ဖို့ ခွင့်ပြုထားမယ်
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