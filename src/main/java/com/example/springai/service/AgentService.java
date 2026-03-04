package com.example.springai.service;

import com.example.springai.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Set;

/**
 * Core agent service that orchestrates multi-step agentic workflows.
 * Supports three providers:
 *   claude-*           → Anthropic (cloud)
 *   Groq model IDs     → Groq via OpenAI-compatible API (cloud, free tier)
 *   everything else    → Ollama (local)
 */
@Service
public class AgentService implements AgentServiceApi {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** Model IDs served by Groq — https://console.groq.com/docs/models */
    static final Set<String> GROQ_MODEL_IDS = Set.of(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
    );

    private final ChatClient anthropicClient;
    /** Null when GROQ_API_KEY is not set. */
    private final ChatClient groqClient;
    /** Null when Ollama is not running locally. */
    private final ChatClient ollamaClient;
    private final boolean ollamaReachable;
    private final int maxIterations;

    public AgentService(
            AnthropicChatModel anthropicChatModel,
            @Autowired(required = false) @Nullable OpenAiChatModel groqChatModel,
            @Autowired(required = false) @Nullable OllamaChatModel ollamaChatModel,
            @Value("${spring.ai.openai.api-key:groq-not-configured}") String groqApiKey,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${agent.max-iterations:10}") int maxIterations,
            @Value("${agent.system-prompt:You are a helpful AI agent with tool-calling skills.}") String systemPrompt) {

        this.anthropicClient = ChatClient.builder(anthropicChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(systemPrompt)
                .defaultToolNames(AgentConfig.ALL_SKILL_NAMES)
                .build();

        // Only create the Groq client when a real API key is present.
        // NOTE: Groq rejects JSON schemas that contain "additionalProperties":false, which
        // Spring AI 1.1.x unconditionally adds to every tool parameter schema.  Until Spring AI
        // provides a way to suppress that field for OpenAI-compatible providers, we omit tool
        // registration for Groq entirely.  Groq therefore works as a fast chat-only model.
        boolean groqKeyPresent = groqChatModel != null
                && !groqApiKey.isBlank()
                && !"groq-not-configured".equals(groqApiKey);
        this.groqClient = groqKeyPresent
                ? ChatClient.builder(groqChatModel)
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .defaultSystem(systemPrompt)
                        // No .defaultToolNames() — Groq rejects tool schemas with additionalProperties:false
                        .build()
                : null;

        this.ollamaClient = ollamaChatModel != null
                ? ChatClient.builder(ollamaChatModel)
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .defaultSystem(systemPrompt)
                        .defaultToolNames(AgentConfig.ALL_SKILL_NAMES)
                        .build()
                : null;

        // Probe Ollama TCP socket at startup — bean exists even when server is down
        this.ollamaReachable = ollamaClient != null && isOllamaReachable(ollamaBaseUrl);

        this.maxIterations = maxIterations;
        log.info("AgentService ready — Anthropic: yes, Groq: {}, Ollama: {}",
                groqClient  != null ? "yes" : "no (set GROQ_API_KEY)",
                ollamaReachable ? "yes" : "no");
    }

    @Override
    public boolean isProviderAvailable(String provider) {
        return switch (provider) {
            case "anthropic" -> true;
            case "groq"     -> groqClient != null;
            case "ollama"   -> ollamaReachable;
            default         -> false;
        };
    }

    /** TCP probe — returns true only if the Ollama server is actually listening. */
    private static boolean isOllamaReachable(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            int port = uri.getPort() > 0 ? uri.getPort() : 11434;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(uri.getHost(), port), 1000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Provider routing ----

    private boolean isGroqModel(String model) {
        return model != null && GROQ_MODEL_IDS.contains(model);
    }

    private boolean isOllamaModel(String model) {
        return model != null && !model.startsWith("claude-") && !GROQ_MODEL_IDS.contains(model);
    }

    private ChatClient clientFor(String model) {
        if (isGroqModel(model)) {
            if (groqClient == null) {
                throw new IllegalStateException(
                        "Groq is not configured. Set the GROQ_API_KEY environment variable. " +
                        "Get a free key at https://console.groq.com");
            }
            return groqClient;
        }
        if (isOllamaModel(model)) {
            if (ollamaClient == null) {
                throw new IllegalStateException(
                        "Ollama is not available. Install from https://ollama.ai and run: ollama pull " + model);
            }
            return ollamaClient;
        }
        return anthropicClient;
    }

    private ChatOptions optionsFor(String model) {
        if (isGroqModel(model))   return OpenAiChatOptions.builder().model(model).build();
        if (isOllamaModel(model)) return OllamaChatOptions.builder().model(model).build();
        return AnthropicChatOptions.builder().model(model).build();
    }

    // ---- API ----

    @Override
    public String executeTask(String userMessage) {
        log.info("Executing agent task: {}", userMessage);
        String response = anthropicClient.prompt()
                .user(userMessage)
                .call()
                .content();
        log.info("Agent response received ({} chars)", response != null ? response.length() : 0);
        return response;
    }

    @Override
    public String executeTask(String userMessage, String systemOverride) {
        log.info("Executing agent task with custom system prompt: {}", userMessage);
        return anthropicClient.prompt()
                .system(systemOverride)
                .user(userMessage)
                .call()
                .content();
    }

    @Override
    public String executeTask(String userMessage, String systemOverride, String model) {
        if (model == null || model.isBlank()) {
            return executeTask(userMessage, systemOverride != null ? systemOverride : "");
        }
        log.info("Executing agent task with model={}: {}", model, userMessage);
        var promptSpec = clientFor(model).prompt().options(optionsFor(model)).user(userMessage);
        if (systemOverride != null && !systemOverride.isBlank()) {
            promptSpec = promptSpec.system(systemOverride);
        }
        return promptSpec.call().content();
    }

    @Override
    public Flux<String> streamTask(String userMessage) {
        log.info("Streaming agent task: {}", userMessage);
        return anthropicClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }

    @Override
    public Flux<String> streamTask(String userMessage, String model) {
        if (model == null || model.isBlank()) {
            return streamTask(userMessage);
        }
        log.info("Streaming agent task with model={}: {}", model, userMessage);
        return clientFor(model).prompt()
                .options(optionsFor(model))
                .user(userMessage)
                .stream()
                .content();
    }
}
