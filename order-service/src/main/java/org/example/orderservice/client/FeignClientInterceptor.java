package org.example.orderservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class FeignClientInterceptor {

    private final Tracer tracer;

    public FeignClientInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 1. Extract authentication data from Security Context
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                // 2. Verify that the user carries a JWT Token
                if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                    String tokenValue = jwtAuthenticationToken.getToken().getTokenValue();

                    // 3. Add token to header for inventory service request
                    template.header("Authorization", "Bearer " + tokenValue);
                }

                // 4. Add tracing headers for distributed tracing
                if (tracer.currentSpan() != null) {
                    template.header("X-B3-TraceId", tracer.currentSpan().context().traceId());
                    template.header("X-B3-SpanId", tracer.currentSpan().context().spanId());
                    template.header("X-B3-ParentSpanId", tracer.currentSpan().context().parentId());
                    template.header("X-B3-Sampled", tracer.currentSpan().context().sampled() ? "1" : "0");
                }
            }
        };
    }
}