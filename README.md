# Spring AI Agent with Skills

An agentic workflow application built with **Spring AI** and **Anthropic Claude**. The agent can autonomously invoke tools (skills) to accomplish multi-step tasks.

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  REST API                        │
│            AgentController                       │
│   POST /api/agent/chat                           │
│   POST /api/agent/task                           │
│   GET  /api/agent/health                         │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│              AgentService                        │
│   ChatClient + System Prompt + Tool Registration │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│              Claude (Anthropic)                   │
│   Decides which tools to call autonomously       │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│                Skills (Tools)                    │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────┐ │
│  │  DateTime    │ │ FileManager  │ │   Shell   │ │
│  │  Skill      │ │ Skill        │ │   Skill   │ │
│  └─────────────┘ └──────────────┘ └───────────┘ │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────┐ │
│  │  WebFetch   │ │   Memory     │ │   Math    │ │
│  │  Skill      │ │   Skill      │ │   Skill   │ │
│  └─────────────┘ └──────────────┘ └───────────┘ │
└─────────────────────────────────────────────────┘
```

## Skills

| Skill | Functions | Description |
|-------|-----------|-------------|
| **DateTimeSkill** | `getCurrentTime`, `calculateDateDifference` | Get current time in any timezone, calculate days between dates |
| **FileManagerSkill** | `readFile`, `writeFile`, `listFiles` | Sandboxed file operations in `agent-workspace/` directory |
| **ShellCommandSkill** | `executeShellCommand` | Run safe, allowlisted shell commands (ls, cat, git, etc.) |
| **WebFetchSkill** | `fetchUrl` | HTTP GET requests to fetch API data or web content |
| **MemorySkill** | `storeMemory`, `retrieveMemory`, `listMemoryKeys` | In-memory key-value store for cross-turn persistence |
| **MathSkill** | `evaluateMath` | Safe mathematical expression evaluation |

## Prerequisites

- Java 21+
- Maven 3.9+
- Anthropic API key

## Quick Start

1. **Set your API key:**
   ```bash
   export ANTHROPIC_API_KEY=your-api-key-here
   ```

2. **Build and run:**
   ```bash
   mvn spring-boot:run
   ```

3. **Send a request:**
   ```bash
   # Simple chat
   curl -X POST http://localhost:8080/api/agent/chat \
     -H "Content-Type: application/json" \
     -d '{"message": "What time is it in Tokyo and New York?"}'

   # Complex task
   curl -X POST http://localhost:8080/api/agent/task \
     -H "Content-Type: application/json" \
     -d '{"message": "List the files in my workspace, get the current date, then write a summary file with what you found."}'

   # Health check
   curl http://localhost:8080/api/agent/health
   ```

## Configuration

Key settings in `src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.anthropic.api-key` | `${ANTHROPIC_API_KEY}` | Anthropic API key |
| `spring.ai.anthropic.chat.options.model` | `claude-sonnet-4-20250514` | Claude model to use |
| `spring.ai.anthropic.chat.options.max-tokens` | `4096` | Max response tokens |
| `agent.max-iterations` | `10` | Max tool-calling iterations per request |
| `agent.system-prompt` | *(see yml)* | System prompt for the agent |

## Running Tests

```bash
mvn test
```

## Project Structure

```
src/main/java/com/example/springai/
├── SpringAiAgentApplication.java    # Boot entry point
├── config/
│   └── AgentConfig.java             # Skill name registry
├── controller/
│   └── AgentController.java         # REST endpoints
├── service/
│   ├── AgentServiceApi.java         # Service interface
│   └── AgentService.java            # ChatClient + tool orchestration
└── skills/
    ├── DateTimeSkill.java           # Time/date functions
    ├── FileManagerSkill.java        # Sandboxed file I/O
    ├── ShellCommandSkill.java       # Safe shell execution
    ├── WebFetchSkill.java           # HTTP fetch
    ├── MemorySkill.java             # Key-value memory
    └── MathSkill.java               # Math evaluator
```

## Adding a New Skill

1. Create a new `@Configuration` class in `com.example.springai.skills`
2. Define `@Bean` methods returning `Function<Input, Output>` with `@Description`
3. Add the bean name to `AgentConfig.ALL_SKILL_NAMES`

Example:
```java
@Configuration
public class WeatherSkill {

    public record WeatherRequest(String city) {}
    public record WeatherResponse(String city, double temperature) {}

    @Bean
    @Description("Get current weather for a city")
    public Function<WeatherRequest, WeatherResponse> getWeather() {
        return request -> {
            // call weather API...
            return new WeatherResponse(request.city(), 22.5);
        };
    }
}
```
