package com.solana.rwa.bridge.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link ApiKeyAuthInterceptor}.
 *
 * <p>Verifies the mutating-route gate: POST/PATCH/PUT/DELETE require a valid
 * {@code X-API-Key} header (401 otherwise) while GET/HEAD/OPTIONS remain public.
 */
class ApiKeyAuthInterceptorTest {

    private static final String API_KEY = "secret-test-key";

    private final ApiKeyAuthInterceptor interceptor = new ApiKeyAuthInterceptor(API_KEY);

    @Test
    void allowsReadOnlyRequestWithoutApiKey() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void allowsMutatingRequestWithValidApiKey() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(ApiKeyAuthInterceptor.API_KEY_HEADER)).thenReturn(API_KEY);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void rejectsMutatingRequestWhenApiKeyMissing() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(ApiKeyAuthInterceptor.API_KEY_HEADER)).thenReturn(null);
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).setStatus(401);
        assertThat(body.toString()).contains("Missing or invalid API key");
    }

    @Test
    void rejectsMutatingRequestWhenApiKeyInvalid() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getHeader(ApiKeyAuthInterceptor.API_KEY_HEADER)).thenReturn("wrong-key");
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).setStatus(401);
    }

    @Test
    void rejectsMutatingRequestWhenNoKeyConfigured() throws Exception {
        ApiKeyAuthInterceptor unconfigured = new ApiKeyAuthInterceptor("");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getHeader(ApiKeyAuthInterceptor.API_KEY_HEADER)).thenReturn("anything");
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        assertThat(unconfigured.preHandle(request, response, new Object())).isFalse();
        verify(response).setStatus(401);
    }
}