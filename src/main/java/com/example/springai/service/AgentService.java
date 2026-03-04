package com.example.springai.service;

import com.example.springai.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Core agent service that orchestrates multi-step agentic workflows.
 * Supports both Anthropic (cloud) and Ollama (local open-source) providers.
 * Routing is based on model name: claude-* → Anthropic, everything else → Ollama.
 */
@Service
public class AgentService implements AgentServiceApi {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient anthropicClient;
    /** Null when Ollama starter is absent or OllamaChatModel bean is not created. */
    private final ChatClient ollamaClient;
    private final int maxIterations;

    public AgentService(
            AnthropicChatModel anthropicChatModel,
            @Autowired(required = false) @Nullable OllamaChatModel ollamaChatModel,
            @Value("${agent.max-iterations:10}") int maxIterations,
            @Value("${agent.system-prompt:You are a helpful AI agent with tool-calling skills.}") String systemPrompt) {

        this.anthropicClient = ChatClient.builder(anthropicChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(systemPrompt)
                .defaultToolNames(AgentConfig.ALL_SKILL_NAMES)
                .build();

        this.ollamaClient = ollamaChatModel != null
                ? ChatClient.builder(ollamaChatModel)
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .defaultSystem(systemPrompt)
                        .defaultToolNames(AgentConfig.ALL_SKILL_NAMES)
                        .build()
                : null;

        this.maxIterations = maxIterations;
        log.info("AgentService ready — Anthropic: yes, Ollama: {}", ollamaClient != null ? "yes" : "no");
    }

    // ---- Provider routing ----

    private boolean isOllamaModel(String model) {
        return model != null && !model.startsWith("claude-");
    }

    private ChatClient clientFor(String model) {
        if (isOllamaModel(model)) {
            if (ollamaClient == null) {
                throw new IllegalStateException(
                        "Ollama is not available. Install from https://ollama.ai and run: ollama pull " + model);
            }
            return ollamaClient;
        }
        return anthropicClient;
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
        var client = clientFor(model);
        var opts = isOllamaModel(model)
                ? OllamaChatOptions.builder().model(model).build()
                : AnthropicChatOptions.builder().model(model).build();
        var promptSpec = client.prompt().options(opts).user(userMessage);
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
        var client = clientFor(model);
        var opts = isOllamaModel(model)
                ? OllamaChatOptions.builder().model(model).build()
                : AnthropicChatOptions.builder().model(model).build();
        return client.prompt()
                .options(opts)
                .user(userMessage)
                .stream()
                .content();
    }
}
