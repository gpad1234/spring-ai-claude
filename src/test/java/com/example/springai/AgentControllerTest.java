package com.example.springai;

import com.example.springai.controller.AgentController;
import com.example.springai.controller.AgentController.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for AgentController (no Spring context or Mockito needed).
 */
class AgentControllerTest {

    /**
     * Simple fake service for testing the controller logic.
     */
    private final AgentController controller = new AgentController(
            new FakeAgentService()
    );

    @Test
    void chatEndpointReturnsResponse() {
        var request = new ChatRequest("Hello");
        ResponseEntity<AgentResponse> response = controller.chat(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("response: Hello", response.getBody().response());
    }

    @Test
    void taskEndpointReturnsResponse() {
        var request = new TaskRequest("What time is it?", null);
        ResponseEntity<AgentResponse> response = controller.task(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("response: What time is it?", response.getBody().response());
    }

    @Test
    void taskEndpointWithSystemPrompt() {
        var request = new TaskRequest("Hello", "Be brief");
        ResponseEntity<AgentResponse> response = controller.task(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("response+system: Hello", response.getBody().response());
    }

    @Test
    void healthEndpointReturnsReady() {
        var response = controller.health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ready", response.getBody().get("status"));
        assertEquals("claude", response.getBody().get("model"));
    }
}
