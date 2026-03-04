package com.example.springai;

import com.example.springai.service.AgentServiceApi;
import reactor.core.publisher.Flux;

/**
 * Fake agent service for unit testing — no Spring context, no Mockito.
 */
class FakeAgentService implements AgentServiceApi {

    @Override
    public String executeTask(String userMessage) {
        return "response: " + userMessage;
    }

    @Override
    public String executeTask(String userMessage, String systemOverride) {
        return "response+system: " + userMessage;
    }

    @Override
    public Flux<String> streamTask(String userMessage) {
        return Flux.just("streamed ", "response: ", userMessage);
    }
}
