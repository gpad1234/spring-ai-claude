# Future Work & Enhancement Ideas

## Why This Beats the Claude CLI

The Claude CLI can *talk about* running a shell command or reading a file. This app *actually runs it* and feeds the result back to Claude. Here's the full breakdown:

1. **Real tool execution** — Claude CLI can *talk about* running a shell command or reading a file. This app *actually runs it* and feeds the result back to Claude. The model sees real output and reasons on it.

2. **Your own sandboxed filesystem** — Claude has no access to your local `agent-workspace/` directory. Here it can read, write, and list files you own.

3. **Persistent in-session memory** — Claude's context window resets per conversation. The `MemorySkill` lets the agent store and retrieve named facts across multiple prompts within a session (e.g. "remember my name is Greg" → used 10 prompts later).

4. **Live web fetching** — Claude's training data has a cutoff. The `WebFetchSkill` lets it fetch a live URL right now and reason on today's content.

5. **Programmable system prompt + tool manifest** — You control exactly what tools Claude sees, what persona it has, and what constraints it operates under. The Anthropic web UI doesn't let you inject structured tool definitions.

6. **Embeddable API** — Other services in your infrastructure can call `POST /api/agent/chat` or stream from `/api/agent/stream`. The Claude CLI is a human interface, not a machine interface.

7. **Extensibility** — Adding a new skill (e.g. querying your database, calling an internal API, sending a Slack message) is a single annotated Java method. You own the integration layer.

---

## Suggested Enhancements

### Tier 1 — High Value, Low Effort

| Enhancement | Description |
|---|---|
| **Multi-turn conversation history** | Add `MessageChatMemoryAdvisor` so each user session accumulates context. Currently every message is stateless. |
| **Configurable shell allowlist** | Move the command allowlist out of code and into `application.yml` so it can be changed without a rebuild. |
| **Streaming skill progress events** | Emit a special SSE event when Claude invokes a tool (e.g. `{type:"tool_call", name:"fetchUrl"}`) so the UI can show *which* skill is running in real time. |
| **Conversation export** | Add a Download button in the UI to save the current chat as a Markdown or JSON file. |
| **Dark/light theme toggle** | Persist the preference in `localStorage`. |

### Tier 2 — Medium Effort, High Impact

| Enhancement | Description |
|---|---|
| **Persistent memory with a database** | Replace the `ConcurrentHashMap` in `MemorySkill` with a Redis or SQLite backend so memory survives restarts. |
| **Session-scoped memory** | Namespace memory keys by session ID so multiple users don't share the same key-value store. |
| **API key / JWT authentication** | Add Spring Security to lock down the API. A single shared API key is the minimum for non-local deployments. |
| **Rate limiting** | Add Resilience4j or Spring Cloud Gateway rate limiting per IP to prevent API cost abuse. |
| **Multiple model support** | Add a model selector to the UI (Claude Sonnet, Claude Haiku, Claude Opus) with cost-vs-speed tradeoffs visible. |
| **RAG (Retrieval-Augmented Generation)** | Add a `VectorStoreSkill` backed by PGVector or Chroma. Documents chunked and embedded at startup; Claude retrieves relevant chunks before answering questions about your content. |
| **Webhook / callback skill** | Let Claude POST results to an external URL, enabling async pipelines and integration with Zapier, Make, or internal services. |

### Tier 3 — Bigger Bets

| Enhancement | Description |
|---|---|
| **Multi-agent orchestration** | Introduce a supervisor agent that delegates sub-tasks to specialised child agents (a "researcher" agent, a "writer" agent, a "coder" agent). Spring AI Agent Utils supports this pattern. |
| **Code execution sandbox** | Add a `CodeExecutionSkill` that runs Claude-generated Java or Python in a Docker container with resource limits, returning stdout/stderr. |
| **Scheduled / autonomous agents** | Use Spring `@Scheduled` to trigger agent runs on a cron, e.g. daily digest, overnight analysis jobs, monitoring alerts. |
| **Vector + graph memory** | Combine a vector store for semantic recall with a knowledge graph (Neo4j) for relational facts — enabling long-term personalised agents. |
| **Browser automation skill** | Wrap Playwright or Selenium as a skill so the agent can navigate web pages, fill forms, and extract data beyond what simple HTTP GET can reach. |
| **Slack / Teams / Discord integration** | Replace or augment the web UI with a bot adapter so the agent is reachable from team chat tools. |
| **User-uploadable context** | Add a file-upload endpoint to the UI so users can drop a PDF or CSV and ask Claude questions about it (chunk → embed → RAG). |
| **Observability stack** | Integrate Micrometer + Zipkin/Tempo for distributed tracing of every tool call, latency histograms per skill, and token-cost dashboards in Grafana. |
| **MCP (Model Context Protocol) server** | Expose the agent's skills as an MCP server so other MCP-compatible clients (Claude Desktop, Cursor, Zed) can call your tools natively. |

---

## Deployment Paths

| Target | Approach |
|---|---|
| **Local demo** | `mvn spring-boot:run` — works today |
| **Docker** | Single `Dockerfile` (multi-stage: Maven build → JRE 21 slim) |
| **Azure Container Apps** | `azd up` with Bicep; auto-scales to zero when idle |
| **Kubernetes** | Helm chart; HorizontalPodAutoscaler on CPU/memory |
| **AWS Lambda (SnapStart)** | Spring Boot 3 + Lambda Web Adapter; SnapStart cuts cold starts |

---

## Limiting Access on Deployment

Three practical layers, from simplest to most robust.

### Option 1 — API Key (quickest, ~1 hour)

Add Spring Security with a custom filter that checks a request header. Callers set `X-API-Key: your-secret`; the UI sends it automatically. Static assets (`/index.html`, fonts) are excluded from the filter.

```yaml
# application.yml
agent.api-key: ${AGENT_API_KEY}
```

```java
@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    @Value("${agent.api-key}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getHeader("X-API-Key");
        if (!apiKey.equals(key)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(req, res);
    }
}
```

### Option 2 — Network-level Restriction (no code changes)

If deploying to Azure Container Apps, AWS, or behind a reverse proxy (nginx, Caddy):

- **VNet / private ingress** — allow traffic only from within your private network; no public internet exposure
- **IP allowlist** — restrict to your office/home IP range at the load balancer or firewall
- **Cloudflare Access / Azure AD App Proxy** — SSO login page in front with zero backend changes

Right choice for internal demos or team tools.

### Option 3 — OAuth2 / OIDC (production-grade)

Add `spring-boot-starter-oauth2-resource-server` and point it at your identity provider (Entra ID, Okta, Google, Keycloak). The UI exchanges for a token via the IdP; the backend validates the JWT on every request. Gives per-user audit logs, token expiry, and revocation.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://login.microsoftonline.com/{tenant}/v2.0
```

### Decision Guide

| Scenario | Approach |
|---|---|
| Local / team demo | IP allowlist or VPN-only ingress |
| Shared internal tool | API key header |
| Customer-facing / multi-user | OAuth2 / OIDC |
