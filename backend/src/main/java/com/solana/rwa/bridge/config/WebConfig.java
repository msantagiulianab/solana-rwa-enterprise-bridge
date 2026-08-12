package com.solana.rwa.bridge.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration and request-interceptor registration for the RWA
 * Enterprise Bridge API.
 *
 * <p>Allows cross-origin requests from:
 * <ul>
 *   <li>Vercel preview and production deployments ({@code https://*.vercel.app})</li>
 *   <li>Render preview and production deployments ({@code https://*.onrender.com})</li>
 *   <li>Angular local development server ({@code http://localhost:4200})</li>
 * </ul>
 *
 * <p>This replaces {@code @CrossOrigin} annotations on individual controllers
 * with a single, auditable, centralized policy. The {@code X-API-Key} mutating-route
 * gate is registered here so every POST/PATCH/PUT/DELETE is authenticated.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "https://*.vercel.app",
                        "https://*.onrender.com",
                        "http://localhost:4200"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Origin",
                        "Content-Type",
                        "Accept",
                        "Authorization",
                        "X-API-Key"
                )
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/api/**");
    }
}