# Deployment Guide — Spring AI Agent

**Last updated:** March 6, 2026

---

## Quick Reference

| Item | Value |
|---|---|
| **Production host** | `root@143.198.110.70` (DigitalOcean droplet) |
| **Remote JAR path** | `/opt/spring-ai/app.jar` |
| **systemd service** | `spring-ai` |
| **Application port** | `8080` |
| **Java version** | 21 |
| **Build tool** | Maven 3.9+ |

---

## 1. Environment Variables

These must be set on the **remote server** for the application to start correctly.

| Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | **Yes** | Anthropic API key for Claude |
| `APP_API_KEY` | **Yes** (production) | Shared secret used by `ApiKeyFilter` to protect API endpoints. Defaults to `dev-insecure-key-change-me` — **must** be changed for any non-local deployment. |
| `GROQ_API_KEY` | No | Groq cloud inference key (defaults to `groq-not-configured` if absent) |
| `OLLAMA_BASE_URL` | No | Ollama server URL (defaults to `http://localhost:11434`) |

On the droplet these are typically set in the systemd unit file (see §3) or in `/etc/environment`.

---

## 2. One-Command Deploy

The repo includes [deploy.sh](deploy.sh) which builds locally and deploys to the droplet:

```bash
# Deploy to the default host (143.198.110.70)
./deploy.sh

# Deploy to a different host
./deploy.sh root@YOUR_DROPLET_IP
```

**What it does:**

1. Runs `mvn package -DskipTests -q` to build the fat JAR locally.
2. Uploads `target/spring-ai-agent-0.0.1-SNAPSHOT.jar` → `/opt/spring-ai/app.jar` on the remote host via `scp`.
3. Runs `systemctl restart spring-ai` on the remote host.
4. Tails the last 30 log lines for quick verification.

---

## 3. Server Setup (First-Time / New Droplet)

### 3.1 Install Java 21

```bash
apt update && apt install -y openjdk-21-jre-headless
java -version   # confirm 21.x
```

### 3.2 Create the application directory

```bash
mkdir -p /opt/spring-ai
```

### 3.3 Set environment variables

Create `/opt/spring-ai/.env` (or add to the systemd unit directly):

```bash
ANTHROPIC_API_KEY=sk-ant-...
APP_API_KEY=your-strong-random-secret
# Optional:
# GROQ_API_KEY=gsk_...
# OLLAMA_BASE_URL=http://localhost:11434
```

### 3.4 Create the systemd service

Create `/etc/systemd/system/spring-ai.service`:

```ini
[Unit]
Description=Spring AI Agent
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/spring-ai
EnvironmentFile=/opt/spring-ai/.env
ExecStart=/usr/bin/java -jar /opt/spring-ai/app.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
systemctl daemon-reload
systemctl enable spring-ai
systemctl start spring-ai
```

### 3.5 Firewall (optional but recommended)

```bash
ufw allow 22/tcp    # SSH
ufw allow 8080/tcp  # Application
ufw enable
```

If you front the app with Nginx (see §5), open port 80/443 instead of 8080.

---

## 4. Common Operations

### View logs

```bash
# Live tail
journalctl -u spring-ai -f

# Last 100 lines
journalctl -u spring-ai -n 100 --no-pager

# Since last boot
journalctl -u spring-ai -b
```

### Restart the service

```bash
systemctl restart spring-ai
```

### Stop the service

```bash
systemctl stop spring-ai
```

### Check service status

```bash
systemctl status spring-ai
```

### Health check (from anywhere)

```bash
curl http://143.198.110.70:8080/api/agent/health
# Expected: {"status":"ready","model":"claude"}
```

---

## 5. Optional: Nginx Reverse Proxy

To serve on port 80/443 with TLS:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # SSE support
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 120s;
    }
}
```

Then use Certbot for HTTPS:

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d your-domain.com
```

---

## 6. API Authentication

All non-public endpoints require the `APP_API_KEY` value. Supply it via:

- **HTTP header:** `X-API-Key: <key>`
- **Query parameter:** `?apiKey=<key>` (needed for SSE / EventSource which cannot set custom headers)

**Public endpoints** (no key required):
- `/` and `/index.html` — Chat UI
- `/mobile.html` — Mobile UI
- `/api/agent/health` — Health check
- `/api/agent/models` — Model list (read-only, needed by UI)
- `/actuator/health` — Spring Actuator

---

## 7. Build & Test Locally

```bash
# Run tests
mvn test

# Build JAR (skip tests for speed)
mvn package -DskipTests

# Run locally
export ANTHROPIC_API_KEY=your-key
mvn spring-boot:run

# Or run the JAR directly
java -jar target/spring-ai-agent-0.0.1-SNAPSHOT.jar
```

The UI is available at `http://localhost:8080`.

---

## 8. Rollback

If a deploy causes issues:

1. SSH into the droplet: `ssh root@143.198.110.70`
2. Replace `/opt/spring-ai/app.jar` with a known-good JAR (keep backups, or re-deploy from a previous Git commit).
3. `systemctl restart spring-ai`

**Tip:** Before deploying, you can back up the current JAR:

```bash
ssh root@143.198.110.70 "cp /opt/spring-ai/app.jar /opt/spring-ai/app.jar.bak"
```

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Service fails to start | Missing `ANTHROPIC_API_KEY` | Check `/opt/spring-ai/.env` and `journalctl -u spring-ai -n 50` |
| `401 Unauthorized` on API calls | Wrong or missing `APP_API_KEY` | Verify header `X-API-Key` matches the value in `.env` |
| Port 8080 unreachable | Firewall blocking | `ufw allow 8080/tcp` or check cloud provider firewall rules |
| `Connection refused` from SSE | Nginx buffering enabled | Add `proxy_buffering off;` to the Nginx location block |
| Out of memory | JVM defaults on small droplet | Add `-Xmx512m` (or appropriate) to `ExecStart` in the systemd unit |
| Groq/Ollama errors | Provider not configured | These are optional; set the env vars or ignore the warnings |
