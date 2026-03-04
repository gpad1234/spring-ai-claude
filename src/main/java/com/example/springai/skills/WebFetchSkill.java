package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

/**
 * Web/HTTP skills for the AI agent.
 * Allows the agent to fetch data from URLs (e.g., APIs, web pages).
 */
@Configuration
public class WebFetchSkill {

    private static final Logger log = LoggerFactory.getLogger(WebFetchSkill.class);
    private static final int MAX_RESPONSE_LENGTH = 10_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record WebFetchRequest(String url) {}
    public record WebFetchResponse(String url, int statusCode, String body, boolean success, String error) {}

    /**
     * Returns true when the hostname resolves to a private/loopback/link-local address
     * (SSRF protection — blocks access to internal cloud metadata & LAN services).
     */
    private boolean isPrivateAddress(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return true;  // block non-http(s) schemes
            }
            String host = uri.getHost();
            if (host == null) return true;
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()      // 10.x, 172.16-31.x, 192.168.x
                    || addr.isLinkLocalAddress()      // 169.254.x (cloud metadata)
                    || addr.isAnyLocalAddress();
        } catch (Exception e) {
            return true;  // block if we can't resolve
        }
    }

    @Bean
    @Description("Fetch content from a URL via HTTP GET. Only public internet URLs are allowed (private IPs and cloud metadata endpoints are blocked). Returns the response status code and body (truncated to 10000 chars).")
    public Function<WebFetchRequest, WebFetchResponse> fetchUrl() {
        return request -> {
            log.info("Skill invoked: fetchUrl({})", request.url());
            try {
                // SSRF protection: block private/loopback/link-local addresses
                if (isPrivateAddress(request.url())) {
                    log.warn("SSRF blocked: {}", request.url());
                    return new WebFetchResponse(request.url(), -1, null, false,
                            "Blocked: private/internal URLs are not permitted.");
                }

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(request.url()))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "SpringAI-Agent/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                String body = response.body();
                if (body != null && body.length() > MAX_RESPONSE_LENGTH) {
                    body = body.substring(0, MAX_RESPONSE_LENGTH) + "\n... [truncated]";
                }

                return new WebFetchResponse(
                        request.url(),
                        response.statusCode(),
                        body,
                        response.statusCode() >= 200 && response.statusCode() < 300,
                        null
                );
            } catch (Exception e) {
                log.error("Failed to fetch URL: {}", e.getMessage());
                return new WebFetchResponse(request.url(), -1, null, false, e.getMessage());
            }
        };
    }
}
