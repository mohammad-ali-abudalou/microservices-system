package org.example.configserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // تعطيل CSRF للسماح للخدمات بطلب الإعدادات
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll() // فتح الرقابة لبروميثيوس
                        .anyRequest().permitAll() // حالياً نتركها مفتوحة للتطوير، وفي الإنتاج نستخدم Basic Auth
                );
        return http.build();
    }
}