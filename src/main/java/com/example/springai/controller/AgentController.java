package com.example.springai.controller;

import com.example.springai.service.AgentServiceApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the agentic AI endpoints.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    /** All available models, grouped by provider. */
    public static final List<ModelInfo> AVAILABLE_MODELS = List.of(
            // ---- Anthropic (cloud) ----
            AgentController.of("claude-opus-4-5",          "Claude Opus 4.5",  "Most capable, best for complex tasks",     "anthropic"),
            AgentController.of("claude-sonnet-4-20250514", "Claude Sonnet 4",  "Balanced performance and speed (default)", "anthropic"),
            AgentController.of("claude-haiku-4-5",         "Claude Haiku 4.5", "Fastest and most compact",                 "anthropic"),
            // ---- Groq — free cloud inference (https://console.groq.com) ----
            AgentController.of("llama-3.3-70b-versatile", "Llama 3.3 70B",  "Meta on Groq · requires GROQ_API_KEY",      "groq"),
            AgentController.of("llama-3.1-8b-instant",    "Llama 3.1 8B",   "Meta on Groq · fastest/cheapest",           "groq"),
            AgentController.of("mixtral-8x7b-32768",      "Mixtral 8x7B",   "Mistral on Groq · 32k context",             "groq"),
            AgentController.of("gemma2-9b-it",            "Gemma 2 9B",     "Google on Groq · requires GROQ_API_KEY",    "groq"),
            // ---- Ollama — local open-source (https://ollama.ai) ----
            AgentController.of("llama3.2",   "Llama 3.2 (8B)",  "Meta · run: ollama pull llama3.2",   "ollama"),
            AgentController.of("mistral",    "Mistral 7B",       "Mistral AI · run: ollama pull mistral",  "ollama"),
            AgentController.of("gemma2",     "Gemma 2 (9B)",     "Google · run: ollama pull gemma2",   "ollama"),
            AgentController.of("phi4",       "Phi-4 (14B)",      "Microsoft · run: ollama pull phi4",  "ollama")
    );

    private final AgentServiceApi agentService;
    private final String defaultModel;

    public AgentController(
            AgentServiceApi agentService,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-20250514}") String defaultModel) {
        this.agentService = agentService;
        this.defaultModel = defaultModel;
    }

    /**
     * POST /api/agent/chat
     * Send a message to the agent and receive a response.
     * The agent may invoke one or more skills/tools to fulfill the request.
     */
    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody ChatRequest request) {
        String response = agentService.executeTask(request.message(), null, request.model());
        return ResponseEntity.ok(new AgentResponse(response));
    }

    /**
     * POST /api/agent/task
     * Execute a complex task with an optional system prompt override.
     */
    @PostMapping("/task")
    public ResponseEntity<AgentResponse> task(@RequestBody TaskRequest request) {
        String response = agentService.executeTask(request.message(), request.systemPrompt(), request.model());
        return ResponseEntity.ok(new AgentResponse(response));
    }

    /**
     * GET /api/agent/health
     * Simple health check for the agent endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ready", "model", defaultModel));
    }

    /**
     * GET /api/agent/models
     * Returns the list of selectable models, each tagged with whether its provider is available.
     */
    @GetMapping("/models")
    public ResponseEntity<ModelsResponse> models() {
        List<ModelInfo> annotated = AVAILABLE_MODELS.stream()
                .map(m -> new ModelInfo(m.id(), m.name(), m.description(), m.provider(),
                        agentService.isProviderAvailable(m.provider()), m.supportsTools()))
                .toList();
        return ResponseEntity.ok(new ModelsResponse(annotated, defaultModel));
    }

    // --- DTOs ---

    public record ChatRequest(String message, String model) {}

    public record TaskRequest(String message, String systemPrompt, String model) {}

    public record AgentResponse(String response) {}

    public record ModelInfo(String id, String name, String description, String provider, boolean available, boolean supportsTools) {}
    // Factory: defaults supportsTools=true (all non-Groq providers support tools)
    public static ModelInfo of(String id, String name, String description, String provider) {
        return new ModelInfo(id, name, description, provider, true, !"groq".equals(provider));
    }

    public record ModelsResponse(List<ModelInfo> models, String defaultModel) {}
}
