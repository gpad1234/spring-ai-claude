package com.example.springai.controller;

import com.example.springai.service.AgentServiceApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing the agentic AI endpoints.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentServiceApi agentService;

    public AgentController(AgentServiceApi agentService) {
        this.agentService = agentService;
    }

    /**
     * POST /api/agent/chat
     * Send a message to the agent and receive a response.
     * The agent may invoke one or more skills/tools to fulfill the request.
     */
    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody ChatRequest request) {
        String response = agentService.executeTask(request.message());
        return ResponseEntity.ok(new AgentResponse(response));
    }

    /**
     * POST /api/agent/task
     * Execute a complex task with an optional system prompt override.
     */
    @PostMapping("/task")
    public ResponseEntity<AgentResponse> task(@RequestBody TaskRequest request) {
        String response;
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            response = agentService.executeTask(request.message(), request.systemPrompt());
        } else {
            response = agentService.executeTask(request.message());
        }
        return ResponseEntity.ok(new AgentResponse(response));
    }

    /**
     * GET /api/agent/health
     * Simple health check for the agent endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ready", "model", "claude"));
    }

    // --- DTOs ---

    public record ChatRequest(String message) {}

    public record TaskRequest(String message, String systemPrompt) {}

    public record AgentResponse(String response) {}
}
