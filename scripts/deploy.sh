#!/usr/bin/env bash
set -euo pipefail

APP_NAME="feishu-admin-ai-bot-clean"
APP_DIR="/opt/${APP_NAME}"
GIT_BRANCH="${GIT_BRANCH:-main}"
GIT_REPO_URL="${GIT_REPO_URL:-https://github.com/SUNZEQIN/feishu-admin-ai-bot-clean.git}"
GIT_PROXY_PREFIX="${GIT_PROXY_PREFIX:-}"

echo "[1/6] 进入项目目录：${APP_DIR}"
cd "${APP_DIR}"

echo "[2/6] 检查 .env"
if [ ! -f ".env" ]; then
  echo ".env 不存在，正在从 .env.example 复制..."
  cp .env.example .env
  echo "请先编辑 .env，然后重新执行本脚本。"
  exit 1
fi

echo "[3/6] 拉取最新代码"
if [ -d ".git" ]; then
  if [ -n "${GIT_PROXY_PREFIX}" ]; then
    GIT_PULL_URL="${GIT_PROXY_PREFIX}${GIT_REPO_URL}"
    echo "使用 Git 代理拉取代码：${GIT_PULL_URL}"
    git pull "${GIT_PULL_URL}" "${GIT_BRANCH}"
  else
    echo "使用 origin 拉取代码：分支=${GIT_BRANCH}"
    git pull origin "${GIT_BRANCH}"
  fi
else
  echo "当前目录不是 Git 仓库，跳过 git pull。"
fi

echo "[5/6] 构建并启动 Java 服务"
docker compose up -d --build

echo "[6/6] 查看服务状态"
docker ps --filter "name=${APP_NAME}"

echo "部署完成。查看日志："
echo "docker logs -f ${APP_NAME}"
