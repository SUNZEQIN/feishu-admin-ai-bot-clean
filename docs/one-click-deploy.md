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
