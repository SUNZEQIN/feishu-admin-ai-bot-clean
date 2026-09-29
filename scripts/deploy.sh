#!/usr/bin/env bash
set -euo pipefail

APP_NAME="feishu-admin-ai-bot-clean"
APP_DIR="/opt/${APP_NAME}"

echo "[1/6] Enter project directory: ${APP_DIR}"
cd "${APP_DIR}"

echo "[2/6] Check .env"
if [ ! -f ".env" ]; then
  echo ".env not found. Copying from .env.example ..."
  cp .env.example .env
  echo "Please edit .env first, then rerun this script."
  exit 1
fi

echo "[3/6] Pull latest code when this is a Git repository"
if [ -d ".git" ]; then
  git pull origin main
else
  echo "Skip git pull because .git directory does not exist."
fi

echo "[4/6] Stop old container with same name if it exists"
if docker ps -a --format '{{.Names}}' | grep -Fxq "${APP_NAME}"; then
  docker stop "${APP_NAME}" || true
  docker rm "${APP_NAME}" || true
fi

echo "[5/6] Build and start Java service"
docker compose up -d --build

echo "[6/6] Show service status"
docker ps --filter "name=${APP_NAME}"

echo "Done. Logs:"
echo "docker logs -f ${APP_NAME}"
