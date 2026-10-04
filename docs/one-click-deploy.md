# clean 项目一键部署说明

本文档记录 `feishu-admin-ai-bot-clean` 在服务器上的部署命令。

## 1. 首次拉取代码

如果服务器能直接访问 GitHub：

```bash
cd /opt
git clone https://github.com/SUNZEQIN/feishu-admin-ai-bot-clean.git feishu-admin-ai-bot-clean
cd /opt/feishu-admin-ai-bot-clean
```

如果服务器访问 GitHub 不稳定，使用代理地址：

```bash
cd /opt
git clone https://ghproxy.net/https://github.com/SUNZEQIN/feishu-admin-ai-bot-clean.git feishu-admin-ai-bot-clean
cd /opt/feishu-admin-ai-bot-clean
```

## 2. 准备配置文件

第一次部署时复制配置模板：

```bash
cp .env.example .env
nano .env
```

至少需要填写：

```ini
MYSQL_PASSWORD=你的密码
FEISHU_APP_ID=cli_xxx
FEISHU_APP_SECRET=xxx
FEISHU_OAUTH_REDIRECT_URI=http://服务器IP:8082/api/feishu/oauth/callback
FEISHU_LLM_API_KEY=你的大模型key
```

## 3. 普通部署

如果服务器能直接访问 GitHub：

```bash
cd /opt/feishu-admin-ai-bot-clean
bash scripts/deploy.sh
```

脚本会按 7 步执行，并且**每一步失败都会直接退出**，不会留下一个"看起来在跑其实没起来"的容器：

| 步骤 | 做什么 | 失败会怎样 |
| --- | --- | --- |
| 1 | 进入 `/opt/feishu-admin-ai-bot-clean` | 目录不存在直接报错 |
| 2 | 检查 `.env` | 缺失时从 `.env.example` 复制并提示你填，然后退出 |
| 3 | `git pull` 最新代码 | 拉取失败直接退出 |
| 4 | 确认外部 Docker 网络 `feishu-net` | 不存在时自动创建，已存在则复用 |
| 5 | `docker compose up -d --build` | 构建或启动失败直接退出 |
| 6 | 轮询 `/api/health` 直到通过 | 超时（默认 120 秒）打印最近 80 行日志并退出 1 |
| 7 | 打印容器状态 | — |

每次部署都会重新构建镜像：Skill 文档在 `src/main/resources/skills/` 下，**改了 Skill 必须重新打包**才会生效。

## 4. 使用 Git 代理部署

如果服务器拉 GitHub 经常超时，使用：

```bash
cd /opt/feishu-admin-ai-bot-clean
GIT_PROXY_PREFIX=https://ghproxy.net/ bash scripts/deploy.sh
```

脚本内部会执行：

```bash
git pull https://ghproxy.net/https://github.com/SUNZEQIN/feishu-admin-ai-bot-clean.git main
```

## 5. 可选参数

可以通过环境变量覆盖默认值：

```bash
GIT_BRANCH=main \
GIT_REPO_URL=https://github.com/SUNZEQIN/feishu-admin-ai-bot-clean.git \
GIT_PROXY_PREFIX=https://ghproxy.net/ \
bash scripts/deploy.sh
```

参数说明：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `GIT_BRANCH` | `main` | 要拉取的分支 |
| `GIT_REPO_URL` | `https://github.com/SUNZEQIN/feishu-admin-ai-bot-clean.git` | 原始 GitHub 仓库地址 |
| `GIT_PROXY_PREFIX` | 空 | Git 代理前缀，例如 `https://ghproxy.net/` |

## 6. 查看服务状态

查看容器：

```bash
docker ps | grep feishu-admin-ai-bot-clean
```

查看日志：

```bash
docker logs --tail=300 -f feishu-admin-ai-bot-clean
```

健康检查：

```bash
curl http://127.0.0.1:8082/api/health
```

期望返回：

```json
{"ok":true,"service":"feishu-admin-ai-bot-clean"}
```

部署脚本会自动做这一步；只有这条返回正常，才算部署成功，而不是「容器在跑」。
