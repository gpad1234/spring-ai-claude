package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory key-value memory/note-taking skill for the AI agent.
 * Allows the agent to persist and retrieve information across conversation turns.
 */
@Configuration
public class MemorySkill {

    private static final Logger log = LoggerFactory.getLogger(MemorySkill.class);

    private final Map<String, String> memory = new ConcurrentHashMap<>();

    // --- Store ---

    public record StoreRequest(String key, String value) {}
    public record StoreResponse(String key, boolean success, String message) {}

    @Bean
    @Description("Store a key-value pair in the agent's memory. Use this to remember facts, intermediate results, or user preferences for later retrieval.")
    public Function<StoreRequest, StoreResponse> storeMemory() {
        return request -> {
            log.info("Skill invoked: storeMemory(key={})", request.key());
            memory.put(request.key(), request.value());
            return new StoreResponse(request.key(), true, "Stored successfully");
        };
    }

    // --- Retrieve ---

    public record RetrieveRequest(String key) {}
    public record RetrieveResponse(String key, String value, boolean found) {}

    @Bean
    @Description("Retrieve a value by key from the agent's memory. Returns the stored value if found.")
    public Function<RetrieveRequest, RetrieveResponse> retrieveMemory() {
        return request -> {
            log.info("Skill invoked: retrieveMemory(key={})", request.key());
            String value = memory.get(request.key());
            return new RetrieveResponse(request.key(), value, value != null);
        };
    }

    // --- List All Keys ---

    public record ListMemoryRequest(String filter) {}
    public record ListMemoryResponse(List<String> keys, int total) {}

    @Bean
    @Description("List all keys currently stored in the agent's memory. Optionally provide a filter substring to match key names.")
    public Function<ListMemoryRequest, ListMemoryResponse> listMemoryKeys() {
        return request -> {
            log.info("Skill invoked: listMemoryKeys(filter={})", request.filter());
            List<String> keys;
            if (request.filter() != null && !request.filter().isBlank()) {
                keys = memory.keySet().stream()
                        .filter(k -> k.contains(request.filter()))
                        .sorted()
                        .toList();
            } else {
                keys = memory.keySet().stream().sorted().toList();
            }
            return new ListMemoryResponse(keys, keys.size());
        };
    }
}
