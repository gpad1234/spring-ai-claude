package com.example.springai.service;

import reactor.core.publisher.Flux;

/**
 * Interface for agent service to support testability.
 */
public interface AgentServiceApi {
    String executeTask(String userMessage);
    String executeTask(String userMessage, String systemOverride);
    String executeTask(String userMessage, String systemOverride, String model);
    Flux<String> streamTask(String userMessage);
    Flux<String> streamTask(String userMessage, String model);
    boolean isProviderAvailable(String provider);
}
