# Technical Specification — Claude AI Agent (Spring AI)

**Version:** 1.0  
**Date:** March 3, 2026  
**Status:** Live

---

## 1. Overview

A full-stack AI agent application that integrates Anthropic's Claude model with a Spring Boot backend and a browser-based chat UI. The agent executes multi-step agentic workflows by autonomously selecting and invoking registered skill functions (tools) to satisfy user requests — with responses streamed token-by-token to the frontend via Server-Sent Events.

---

## 2. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Application Framework | Spring Boot | 3.4.2 |
| AI Framework | Spring AI | 1.1.2 |
| LLM Provider | Anthropic Claude | claude-sonnet-4-20250514 |
| Streaming Protocol | Server-Sent Events (SSE) | — |
| Reactive Streams | Project Reactor (`Flux`) | via Spring WebFlux |
| Build Tool | Apache Maven | 3.9+ |
| Frontend | Vanilla HTML/CSS/JS | — |
| Markdown Rendering | marked.js | CDN |
| Syntax Highlighting | highlight.js | 11.9.0 (github-dark theme) |
| Typography | Inter + JetBrains Mono | Google Fonts |

---

## 3. Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         Browser UI                                │
│   Chat interface · SSE streaming · Markdown + code rendering     │
└────────────────────────────┬─────────────────────────────────────┘
                             │  HTTP / SSE
┌────────────────────────────▼─────────────────────────────────────┐
│                     Spring Boot (port 8080)                        │
│                                                                    │
│   AgentController          StreamController                        │
│   POST /api/agent/chat     GET  /api/agent/stream?message=...     │
│   POST /api/agent/task     (SSE — text/event-stream)              │
│   GET  /api/agent/health                                          │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                       AgentService                                 │
│   ChatClient · System prompt · defaultToolNames (11 functions)    │
│   executeTask()  executeTask(msg, system)  streamTask() → Flux   │
└────────────────────────────┬─────────────────────────────────────┘
                             │  Spring AI tool-calling protocol
┌────────────────────────────▼─────────────────────────────────────┐
│                  Anthropic Claude API                              │
│   Autonomous tool selection · multi-turn reasoning               │
└────────────────────────────┬─────────────────────────────────────┘
                             │  invokes
┌────────────────────────────▼─────────────────────────────────────┐
│                     Agent Skills (Tools)                           │
│   DateTimeSkill · FileManagerSkill · ShellCommandSkill           │
│   WebFetchSkill · MemorySkill · MathSkill                        │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Backend — REST API

### 4.1 Endpoints

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/agent/chat` | Send a message; blocking response | `{"message": "..."}` | `{"response": "..."}` |
| `POST` | `/api/agent/task` | Execute task with optional system prompt | `{"message": "...", "systemPrompt": "..."}` | `{"response": "..."}` |
| `GET` | `/api/agent/stream` | SSE stream of response tokens | `?message=...` | `text/event-stream` |
| `GET` | `/api/agent/health` | Health/readiness check | — | `{"status": "ready", "model": "claude"}` |

### 4.2 SSE Event Protocol

The `/api/agent/stream` endpoint emits a sequence of JSON payloads as SSE data frames:

| Event type | Payload | Meaning |
|---|---|---|
| `thinking` | `{"type":"thinking"}` | Agent started processing; show loading state |
| `token` | `{"type":"token","content":"..."}` | One streamed text chunk from Claude |
| `done` | `{"type":"done"}` | Stream complete; all tokens delivered |
| `error` | `{"type":"error","message":"..."}` | A server-side or Claude API error occurred |

**SSE timeout:** 120 seconds.

---

## 5. Backend — Service Layer

### 5.1 AgentService

Implements `AgentServiceApi`. Built around Spring AI's `ChatClient` configured with:

- **System prompt** — injected from `agent.system-prompt` property; instructs Claude on tool use, reasoning transparency, and fallback behaviour.
- **`SimpleLoggerAdvisor`** — logs all prompts and completions at DEBUG level for observability.
- **`defaultToolNames`** — registers all 11 skill function names so Claude has the full tool manifest on every request.

**Methods:**

```java
String executeTask(String userMessage)
String executeTask(String userMessage, String systemOverride)
Flux<String> streamTask(String userMessage)
```

`streamTask` calls `ChatClient.prompt().stream().content()`, returning a `Flux<String>` of token chunks.

### 5.2 AgentServiceApi

Interface separating the service contract from implementation, enabling no-framework unit testing via plain Java fakes.

---

## 6. Agent Skills (Tools)

Skills are Spring `@Configuration` classes containing `@Bean` methods that return `Function<Request, Response>` and are annotated with `@Description`. Spring AI converts these into tool definitions sent to Claude in each API request. Claude autonomously decides when and how to call them.

All tool names are registered in `AgentConfig.ALL_SKILL_NAMES`:

```java
"getCurrentTime", "calculateDateDifference",
"readFile", "writeFile", "listFiles",
"executeShellCommand",
"fetchUrl",
"storeMemory", "retrieveMemory", "listMemoryKeys",
"evaluateMath"
```

### 6.1 DateTimeSkill

| Function | Input | Output | Description |
|---|---|---|---|
| `getCurrentTime` | timezone (optional, IANA) | dateTime, timezone, dayOfWeek, epochMillis | Current timestamp in any IANA timezone; defaults to UTC |
| `calculateDateDifference` | startDate, endDate (YYYY-MM-DD) | days, weeks, startDate, endDate | Calculates calendar days and whole weeks between two dates |

### 6.2 FileManagerSkill

Operates inside a sandboxed `agent-workspace/` directory located relative to the working directory. Path traversal attacks (e.g. `../../etc/passwd`) are blocked by normalisation validation.

| Function | Input | Output | Description |
|---|---|---|---|
| `readFile` | filename | filename, content, success, error | Reads file content as UTF-8 string |
| `writeFile` | filename, content | filename, success, error | Creates or overwrites a file; parent directories auto-created |
| `listFiles` | directory (optional) | directory, files[], success, error | Lists files and subdirectories; directories appended with `/` |

**Security:** Resolved path is validated with `path.startsWith(WORKSPACE)` before any I/O.

### 6.3 ShellCommandSkill

| Function | Input | Output | Description |
|---|---|---|---|
| `executeShellCommand` | command | command, output, error, exitCode, success | Executes an allowlisted shell command |

**Allowlist:** `ls`, `cat`, `echo`, `date`, `whoami`, `pwd`, `uname`, `wc`, `head`, `tail`, `grep`, `find`, `which`, `env`, `java`, `mvn`, `git`

**Timeout:** 30 seconds. Commands that exceed the timeout are forcibly terminated.

**Security:** The base command is extracted and compared against the allowlist before execution; commands containing path separators are checked on the basename.

### 6.4 WebFetchSkill

| Function | Input | Output | Description |
|---|---|---|---|
| `fetchUrl` | url | url, statusCode, body, success, error | HTTP GET; follows redirects; 15s timeout; body truncated to 10,000 chars |

Built using `java.net.http.HttpClient`. User-agent is set to `SpringAI-Agent/1.0`.

### 6.5 MemorySkill

In-memory key-value store backed by `ConcurrentHashMap`. Scoped to the application lifetime (resets on restart).

| Function | Input | Output | Description |
|---|---|---|---|
| `storeMemory` | key, value | key, success, message | Stores or overwrites a string value |
| `retrieveMemory` | key | key, value, found | Retrieves a stored value by key |
| `listMemoryKeys` | filter (optional) | keys[], total | Lists all keys; optional substring filter |

### 6.6 MathSkill

| Function | Input | Output | Description |
|---|---|---|---|
| `evaluateMath` | expression | expression, result, success, error | Evaluates arithmetic expressions |

**Operators:** `+`, `-`, `*`, `/`, `%`, `^` (exponentiation), parentheses.

**Security:** Input is validated against regex `[0-9+\-*/().\s%^]+` before evaluation. A hand-rolled recursive descent parser is used — no `eval`, no `ScriptEngine`.

**Output formatting:** Results that are whole numbers are returned as integers (e.g. `"10"` not `"10.0"`).

---

## 7. Configuration

All settings are in `src/main/resources/application.yml`.

| Property | Default | Description |
|---|---|---|
| `spring.ai.anthropic.api-key` | `${ANTHROPIC_API_KEY}` | Anthropic API key (env var) |
| `spring.ai.anthropic.chat.options.model` | `claude-sonnet-4-20250514` | Claude model version |
| `spring.ai.anthropic.chat.options.max-tokens` | `4096` | Maximum completion tokens |
| `spring.ai.anthropic.chat.options.temperature` | `0.7` | Sampling temperature |
| `agent.max-iterations` | `10` | Max agentic iterations (tracked; enforcement TBD) |
| `agent.system-prompt` | _(see yml)_ | System prompt injected on every request |
| `server.port` | `8080` | HTTP server port |
| `logging.level.org.springframework.ai` | `DEBUG` | AI framework log verbosity |

---

## 8. Frontend — Chat UI

Single-page application served as a static file from `src/main/resources/static/index.html`.

### 8.1 Layout

| Region | Description |
|---|---|
| **Header** | Brand logo, application name, live status badge, model badge, Clear button |
| **Sidebar** (280px) | Skill chips, message/skill stats, 6 quick-demo buttons, config summary |
| **Chat panel** | Scrollable message thread; welcome screen when empty |
| **Input bar** | Auto-resizing textarea, send button, keyboard hint |

### 8.2 Feature Highlights

**Real token streaming**  
Uses the browser `EventSource` API against the SSE endpoint. Tokens are appended to a `rawBuffer` and the entire buffer is re-rendered through `marked.parse()` on each chunk, so markdown takes shape progressively as Claude types.

**Markdown rendering**  
Full CommonMark rendering via marked.js including headings, paragraphs, bold/italic, inline code, fenced code blocks, blockquotes, tables, horizontal rules, and links.

**Syntax-highlighted code blocks**  
highlight.js with `github-dark` theme. A custom marked renderer wraps each code block with a language label bar and a one-click **copy** button (copies raw source text to clipboard).

**Thinking indicator**  
Displayed as soon as a request is sent (via the `thinking` SSE event). Animated bouncing dots disappear when the first `token` event arrives.

**Skill animation**  
While the agent is processing, the sidebar skill chips cycle through a glowing/pulsing animation to indicate that tools may be invoked.

**Quick-demo buttons**  
Six pre-loaded prompts covering each skill category: DateTime, Math, Files + Shell, WebFetch, Memory, and a multi-skill workflow. Clicking injects the prompt directly into the chat.

**Welcome screen**  
Shown on load and after clearing chat. Features a gently floating logo and feature badges. Disappears on the first message.

**Error handling**  
SSE `error` events and `EventSource.onerror` both surface an inline error bubble so the user understands when the API key is missing or the server is unreachable.

**Auto-resize textarea**  
Grows with content up to 140px; keyboard shortcut `Enter` sends, `Shift+Enter` inserts a newline.

**Health check**  
On page load, `GET /api/agent/health` is called; the status badge shows `Connected` (green dot, pulsing) on success or `Offline` (red) on failure.

**Responsive**  
Sidebar collapses below 700px viewport width.

### 8.3 Design System

```
Background:   #0a0b0f   Surface:  #13141a / #1a1b23
Accent:       #7c6af7   Accent2:  #5b9cf6
Text:         #e8e9f0   Muted:    #7a7b8a
Green:        #4ade80   Red:      #f87171
Border:       #2a2b35
Font body:    Inter (300–700)
Font mono:    JetBrains Mono (400, 500)
```

---

## 9. Testing

### 9.1 Test Coverage

| Test class | Tests | Scope |
|---|---|---|
| `AgentControllerTest` | 4 | Controller routing, request/response wiring, health endpoint |
| `DateTimeSkillTest` | 3 | UTC default, timezone override, date-diff calculation |
| `MathSkillTest` | 4 | Addition, complex expression, exponentiation, invalid input rejection |

**Total: 11 tests · 0 failures**

### 9.2 Test Philosophy

Tests are pure unit tests with **no Spring application context** and **no Mockito**. `FakeAgentService` implements `AgentServiceApi` directly, avoiding JVM byte-buddy issues on Java 25. `AgentController` accepts the interface, not the concrete class, making it trivially injectable.

### 9.3 Running Tests

```bash
mvn test
```

---

## 10. Project Structure

```
spring-ai/
├── pom.xml                              Maven build descriptor
├── TECH_SPEC.md                         This document
├── README.md                            Quick-start guide
└── src/
    ├── main/
    │   ├── java/com/example/springai/
    │   │   ├── SpringAiAgentApplication.java     Boot entry point
    │   │   ├── config/
    │   │   │   └── AgentConfig.java               Tool name registry
    │   │   ├── controller/
    │   │   │   ├── AgentController.java            Blocking REST endpoints
    │   │   │   └── StreamController.java           SSE streaming endpoint
    │   │   ├── service/
    │   │   │   ├── AgentServiceApi.java            Service interface
    │   │   │   └── AgentService.java               ChatClient orchestration
    │   │   └── skills/
    │   │       ├── DateTimeSkill.java              getCurrentTime, calculateDateDifference
    │   │       ├── FileManagerSkill.java           readFile, writeFile, listFiles
    │   │       ├── ShellCommandSkill.java          executeShellCommand
    │   │       ├── WebFetchSkill.java              fetchUrl
    │   │       ├── MemorySkill.java                storeMemory, retrieveMemory, listMemoryKeys
    │   │       └── MathSkill.java                  evaluateMath
    │   └── resources/
    │       ├── application.yml                     Configuration
    │       └── static/
    │           └── index.html                      Single-page chat UI
    └── test/
        └── java/com/example/springai/
            ├── FakeAgentService.java               Test double for AgentServiceApi
            ├── AgentControllerTest.java            Controller unit tests
            └── skills/
                ├── DateTimeSkillTest.java
                └── MathSkillTest.java
```

---

## 11. Running the Application

```bash
# 1. Set API key
export ANTHROPIC_API_KEY=your-api-key

# 2. Start server
mvn spring-boot:run

# 3. Open UI
open http://localhost:8080

# 4. Example API calls
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What time is it in Tokyo?"}'

curl "http://localhost:8080/api/agent/stream?message=Calculate+2%5E10" \
  -H "Accept: text/event-stream"
```

---

## 12. Extending the Application

### Adding a New Skill

1. Create a `@Configuration` class in `com.example.springai.skills`
2. Define one or more `@Bean` methods returning `Function<Request, Response>` with `@Description`
3. Add the bean method name(s) to `AgentConfig.ALL_SKILL_NAMES`

```java
@Configuration
public class WeatherSkill {

    public record WeatherRequest(String city) {}
    public record WeatherResponse(String city, String condition, double tempCelsius) {}

    @Bean
    @Description("Get current weather conditions for a city. Returns temperature and condition.")
    public Function<WeatherRequest, WeatherResponse> getWeather() {
        return request -> {
            // call a weather API...
            return new WeatherResponse(request.city(), "Sunny", 21.5);
        };
    }
}
```

No other wiring is required — Spring AI discovers the bean automatically.

---

## 13. Known Constraints and Future Work

| Area | Current state | Potential improvement |
|---|---|---|
| Memory persistence | In-memory (`ConcurrentHashMap`); resets on restart | Replace with Redis or a database |
| Conversation history | Single-turn (no message history sent back) | Add `MessageChatMemoryAdvisor` for multi-turn context |
| Authentication | None — open to any client on the network | Add Spring Security with API key or OAuth |
| Shell allowlist | Static list of 18 commands | Make configurable via `application.yml` |
| File workspace | Local disk only | Replace with an S3-compatible object store |
| WebFetch | Unauthenticated HTTP GET only | Add header injection for authenticated APIs |
| Max iterations | Tracked but not enforced in streaming | Wire to a `maxTokens` budget or iteration counter |
| Rate limiting | None | Add `Resilience4j` rate limiter per IP |
| Observability | `SimpleLoggerAdvisor` + SLF4J | Add `Micrometer` traces and Spring AI metrics |
