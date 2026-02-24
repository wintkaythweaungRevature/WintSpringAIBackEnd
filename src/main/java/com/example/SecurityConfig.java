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
            // 1. CSRF ကို Disable လုပ်ခြင်း (REST API အတွက် လိုအပ်သည်)
            .csrf(AbstractHttpConfigurer::disable)
            // 2. CORS setting ကို အောက်က Bean နဲ့ ချိတ်ဆက်ခြင်း
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 3. Request permissions သတ်မှတ်ခြင်း
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/audio/**").permitAll() // Transcription API ကို ခွင့်ပြုရန်
                .requestMatchers("/error").permitAll()         // ✅ Error တက်ရင် 403 မပြဘဲ Error message ပြရန် လိုအပ်သည်
                .anyRequest().permitAll()                     // ကျန်တဲ့ request အားလုံးကိုလည်း test အနေနဲ့ ခွင့်ပြုထားသည်
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
            "https://wintaibot.com"
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