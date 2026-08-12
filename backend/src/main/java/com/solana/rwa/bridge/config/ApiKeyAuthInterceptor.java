package com.solana.rwa.bridge.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Enforces an {@code X-API-Key} gate on every mutating route
 * ({@code POST}/{@code PATCH}/{@code PUT}/{@code DELETE}).
 *
 * <p>Read-only endpoints (GET/HEAD/OPTIONS) are deliberately left public for the
 * audit/ledger viewers; any endpoint that mutates state MUST present a valid key.
 * The expected key is injected from {@code security.api-key} ({@code SECURITY_API_KEY}
 * env var) at runtime and is never committed to source control.
 *
 * <p>A missing, blank, or mismatched key is rejected with {@code 401 Unauthorized}
 * via a small, sanitized JSON body that does not leak any internal state.
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-API-Key";

    private static final Set<String> MUTATING_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name());

    private final String apiKey;

    public ApiKeyAuthInterceptor(@Value("${security.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!isValid(providedKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid API key\"}");
            return false;
        }
        return true;
    }

    private boolean isValid(String providedKey) {
        return apiKey != null && !apiKey.isBlank() && apiKey.equals(providedKey);
    }
}