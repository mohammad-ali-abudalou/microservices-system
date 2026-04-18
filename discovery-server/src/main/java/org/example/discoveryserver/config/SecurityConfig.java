package org.example.discoveryserver.config;

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
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/eureka/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // السطر التالي سيسمح بفتح واجهة يوريكا في المتصفح بدون Password
                        //.requestMatchers("/").permitAll()
                        //.requestMatchers("/lastn/**", "/js/**", "/css/**", "/fonts/**", "/images/**").permitAll()
                        //.anyRequest().authenticated()
                )
                .httpBasic(org.springframework.security.config.Customizer.withDefaults());


        return http.build();
    }
}