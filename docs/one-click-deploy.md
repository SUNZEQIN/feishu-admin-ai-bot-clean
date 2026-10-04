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

## 5.1 同一台服务器跑正式环境和测试环境

部署目录默认取「脚本所在仓库的根目录」，容器名默认跟随目录名，所以只要目录名不同，两个环境天然隔离：

```bash
# 正式环境
cd /opt/feishu-admin-ai-bot-clean && bash scripts/deploy.sh

# 测试环境（目录名不同 → 容器名 / compose 项目名 / 镜像名都不同）
cd /opt/feishu-admin-ai-bot-clean-test && bash scripts/deploy.sh
```

**必须改的 4 个配置**（在测试环境的 `.env` 里）：

| 配置 | 正式环境 | 测试环境 | 不改会怎样 |
| --- | --- | --- | --- |
| `SERVER_PORT` | `8082` | `8091` | 端口被占用，容器起不来 |
| `CONTAINER_NAME` | `feishu-admin-ai-bot-clean` | `feishu-admin-ai-bot-clean-test` | 容器名冲突，报 name already in use |
| `FEISHU_OAUTH_REDIRECT_URI` | `...:8082/...` | `...:8091/...` | 扫码授权后回调打不开 |
| `MYSQL_URL`（库名部分） | `.../feishu_admin_bot?...` | `.../feishu_admin_bot_test?...` | 测试环境直接读写正式库的数据（容器不报错，最危险） |

`MYSQL_URL` 这一条特别容易漏：库名写错/没改时**服务照样启动、健康检查照样通过**，只是数据落进了正式库，
所以只能靠上线后核对连接落点来发现 —— 见下面「验证环境隔离」。

选端口时注意同一台服务器上已有的占用：`8081` 是 Dify 的 nginx，`8082` 是正式环境，`8090` 是
`ecommerce-mcp-server`（也正是机器人自己通过 `FEISHU_ECOMMERCE_MCP_BASE_URL` 调用的后端）。
把机器人放到 `8090` 会直接报 `port already allocated`，而且会压到自己的依赖上。

改端口后除了 `.env`，还要**同步改飞书开放平台里 OAuth 重定向 URL 的白名单**，否则扫码授权会失败。

注意：**飞书事件回调地址只能指向其中一个环境**。两个容器同时跑不会「双份回复」，但只有回调地址指向的那个环境能收到消息。切环境要去飞书开放平台改事件订阅地址，并在云服务器安全组放行对应端口。

### 5.2 验证环境隔离

`MYSQL_URL` 配错不会报错，所以每次改完数据库配置，都要**从数据库侧回头看谁连了哪个库**：

```bash
# 1. 看每个容器在 feishu-net 里的 IP
docker inspect -f '{{.Name}} -> {{.NetworkSettings.Networks.feishu-net.IPAddress}}' \
  $(docker ps -q --filter "name=feishu-admin-ai-bot-clean")

# 2. 看每个连接落在哪个库（用机器人自己的账号即可）
docker exec -e MYSQL_PWD="$(grep -E '^MYSQL_PASSWORD=' .env | cut -d= -f2-)" \
  mysql mysql -ufeishu_bot -N -e \
  "SELECT host, db, command FROM information_schema.processlist WHERE user='feishu_bot'"
```

期望结果：正式容器的 IP 只出现在 `feishu_admin_bot`，测试容器的 IP 只出现在 `feishu_admin_bot_test`。
两边的 IP 混在同一个库里，就说明 `.env` 的库名没改，或者改了没重建容器
（`env_file` 只在容器创建时读取，**必须 `docker compose up -d --force-recreate`**，只 `restart` 不生效）。

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
