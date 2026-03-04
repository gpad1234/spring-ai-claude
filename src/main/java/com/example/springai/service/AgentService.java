package com.example.springai.service;

import com.example.springai.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Core agent service that orchestrates multi-step agentic workflows.
 * Uses Spring AI's ChatClient with tool-calling to let Claude invoke skills autonomously.
 */
@Service
public class AgentService implements AgentServiceApi {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final int maxIterations;

    public AgentService(
            ChatClient.Builder chatClientBuilder,
            @Value("${agent.max-iterations:10}") int maxIterations,
            @Value("${agent.system-prompt:You are a helpful AI agent with tool-calling skills.}") String systemPrompt) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(systemPrompt)
                .defaultToolNames(AgentConfig.ALL_SKILL_NAMES)
                .build();
        this.maxIterations = maxIterations;
    }

    /**
     * Execute an agentic task. Claude will autonomously decide which tools/skills
     * to invoke and will iterate until the task is complete.
     */
    public String executeTask(String userMessage) {
        log.info("Executing agent task: {}", userMessage);

        String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        log.info("Agent response received ({} chars)", response != null ? response.length() : 0);
        return response;
    }

    /**
     * Execute a task with a specific system-level instruction override.
     */
    public String executeTask(String userMessage, String systemOverride) {
        log.info("Executing agent task with custom system prompt: {}", userMessage);

        String response = chatClient.prompt()
                .system(systemOverride)
                .user(userMessage)
                .call()
                .content();

        return response;
    }

    /**
     * Stream the agent response token by token.
     * Tool calls are resolved internally before streaming begins.
     */
    public reactor.core.publisher.Flux<String> streamTask(String userMessage) {
        log.info("Streaming agent task: {}", userMessage);
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }
}
