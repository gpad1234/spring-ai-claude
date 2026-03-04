package com.example.springai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter that requires a valid API key on all non-public endpoints.
 *
 * Accepts the key via:
 *   - HTTP header:      X-API-Key: <key>
 *   - Query parameter:  ?apiKey=<key>   (needed for EventSource / SSE which cannot set headers)
 *
 * Configure key:  APP_API_KEY env var  →  agent.api-key in application.yml
 */
@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private static final String HEADER = "X-API-Key";
    private static final String PARAM  = "apiKey";

    private final String expectedKey;

    public ApiKeyFilter(@Value("${agent.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Public — no key needed
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Accept key from header (REST calls) or query param (EventSource / SSE)
        String key = request.getHeader(HEADER);
        if (key == null) {
            key = request.getParameter(PARAM);
        }

        if (key == null || !key.equals(expectedKey)) {
            log.warn("Rejected {} {} – bad or missing API key (IP: {})",
                    request.getMethod(), path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized – supply a valid X-API-Key header (or apiKey query param for SSE)\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/favicon.ico")
                || path.startsWith("/static/")
                || path.equals("/api/agent/health")
                || path.equals("/api/agent/models")   // read-only, needed for UI startup
                || path.startsWith("/actuator/health");
    }
}
