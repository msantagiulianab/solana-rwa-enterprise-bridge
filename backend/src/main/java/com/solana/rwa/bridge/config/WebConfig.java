package com.solana.rwa.bridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for the RWA Enterprise Bridge API.
 * <p>
 * Allows cross-origin requests from:
 * <ul>
 *   <li>Vercel preview and production deployments ({@code https://*.vercel.app})</li>
 *   <li>Render preview and production deployments ({@code https://*.onrender.com})</li>
 *   <li>Angular local development server ({@code http://localhost:4200})</li>
 * </ul>
 * This replaces {@code @CrossOrigin} annotations on individual controllers
 * with a single, auditable, centralized policy.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "https://*.vercel.app",
                        "https://*.onrender.com",
                        "http://localhost:4200"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}