package com.example.springai.service;

import reactor.core.publisher.Flux;

/**
 * Interface for agent service to support testability.
 */
public interface AgentServiceApi {
    String executeTask(String userMessage);
    String executeTask(String userMessage, String systemOverride);
    Flux<String> streamTask(String userMessage);
}
