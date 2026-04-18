package org.example.orderservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class FeignClientInterceptor {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 1. استخراج بيانات المصادقة من الـ Security Context
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                // 2. التحقق من أن المستخدم يحمل JWT Token
                if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                    String tokenValue = jwtAuthenticationToken.getToken().getTokenValue();

                    // 3. إضافة التوكن في الـ Header للطلب المتوجه لخدمة المخزون
                    template.header("Authorization", "Bearer " + tokenValue);
                }
            }
        };
    }
}