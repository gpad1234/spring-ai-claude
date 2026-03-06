#!/usr/bin/env bash
# deploy.sh — build locally and deploy to a remote droplet via SSH
# Usage: ./deploy.sh root@YOUR_DROPLET_IP

set -euo pipefail

HOST="${1:-root@143.198.110.70}"
JAR="target/spring-ai-agent-0.0.1-SNAPSHOT.jar"
REMOTE_DIR="/opt/spring-ai"
SERVICE="spring-ai"

# Usage: ./deploy.sh [user@host]  (defaults to root@143.198.110.70)

echo "==> Building JAR..."
mvn package -DskipTests -q

echo "==> Uploading $JAR to $HOST:$REMOTE_DIR/app.jar ..."
ssh "$HOST" "mkdir -p $REMOTE_DIR"
scp "$JAR" "$HOST:$REMOTE_DIR/app.jar"

echo "==> Restarting $SERVICE on $HOST ..."
ssh "$HOST" "systemctl restart $SERVICE"

echo "==> Last 30 log lines:"
ssh "$HOST" "journalctl -u $SERVICE -n 30 --no-pager"

echo ""
echo "Done. Service is running on $HOST:8080"
