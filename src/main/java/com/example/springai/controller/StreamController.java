package com.example.springai.controller;

import com.example.springai.service.AgentServiceApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE streaming endpoint — pushes tokens from Claude's response in real time.
 */
@RestController
@RequestMapping("/api/agent")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final AgentServiceApi agentService;
    private final ObjectMapper objectMapper;

    public StreamController(AgentServiceApi agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/agent/stream?message=...
     * Server-Sent Events endpoint. Streams tokens as JSON events.
     * Event types: thinking | token | error | done
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String message,
                             @RequestParam(required = false) String model) {
        SseEmitter emitter = new SseEmitter(120_000L);

        // Send "thinking" event immediately so the UI can show loading state
        try {
            emitter.send(SseEmitter.event().data(toJson(Map.of("type", "thinking"))));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        log.info("SSE stream started for message: {} (model={})", message, model != null ? model : "default");

        agentService.streamTask(message, model)
                .subscribe(
                        token -> {
                            try {
                                String json = toJson(Map.of("type", "token", "content", token));
                                emitter.send(SseEmitter.event().data(json));
                            } catch (IOException e) {
                                log.warn("SSE send failed: {}", e.getMessage());
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            if (error instanceof WebClientResponseException wcre) {
                                log.error("SSE stream HTTP error {}: {}",
                                        wcre.getStatusCode(), wcre.getResponseBodyAsString());
                            } else {
                                log.error("SSE stream error: {}", error.getMessage());
                            }
                            try {
                                emitter.send(SseEmitter.event().data(
                                        toJson(Map.of("type", "error", "message",
                                                error.getMessage() != null ? error.getMessage() : "Unknown error"))));
                            } catch (IOException ignored) {}
                            emitter.completeWithError(error);
                        },
                        () -> {
                            log.info("SSE stream completed");
                            try {
                                emitter.send(SseEmitter.event().data(toJson(Map.of("type", "done"))));
                            } catch (IOException ignored) {}
                            emitter.complete();
                        }
                );

        return emitter;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"serialization failed\"}";
        }
    }
}
