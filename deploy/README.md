# Phase E 部署手册

部署目标：`https://finhub.tax` 上跑起完整后端 + admin 前端。HTTPS 由 Let's Encrypt 签发。

---

## 0. 服务器准备（一次性）

```bash
# SSH 进服务器（PowerShell 客户端示例）
ssh -i $env:USERPROFILE\.ssh\wecom_bft_deploy -o IdentitiesOnly=yes root@8.147.58.55

# 安装 Docker（Debian/Ubuntu）
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# 确认 docker compose v2 可用
docker compose version
```

## 1. 克隆仓库

```bash
mkdir -p /opt && cd /opt
git clone git@github.com:qgreener/wecom-bft-system-new.git wecom-bft-system
cd wecom-bft-system/deploy
```

> 如果服务器还没配 GitHub SSH key，可以临时用 HTTPS（GitHub 现在要 PAT 或 fine-grained token）：`git clone https://github.com/qgreener/wecom-bft-system-new.git wecom-bft-system`

## 2. 准备 `.env`

```bash
cp .env.production.example .env
vi .env
```

至少要填的几项：
- `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD` （数据库密码，随便起强密码）
- `WECOM_CORP_ID`、`WECOM_AGENT_ID`、`WECOM_AGENT_SECRET` （企业微信自建应用）
- `WECHAT_MINIAPP_APP_ID`、`WECHAT_MINIAPP_APP_SECRET` （小程序）
- `WECOM_OAUTH_REDIRECT_URI=https://finhub.tax/api/admin/auth/wecom-oauth/callback`

第一次部署建议保持 `INTEGRATION_WECOM_AUTH_MODE=mock` / `INTEGRATION_WECHAT_MINIAPP_AUTH_MODE=mock`，先把链路跑通；HTTPS 证书 OK 后再切 `real` 重启。

## 3. 拉起服务（HTTP 80 阶段）

```bash
chmod +x scripts/deploy.sh
./scripts/deploy.sh
```

完成后访问 `http://finhub.tax/admin/`，能看到登录页就 OK。

调试技巧：
```bash
docker compose --env-file .env logs -f backend
docker compose --env-file .env logs -f nginx
docker compose --env-file .env ps
```

## 4. 域名解析

到 DNS 控制台把 `finhub.tax` 和 `www.finhub.tax` 的 A 记录都指向 `8.147.58.55`。

## 5. 签发 HTTPS 证书（Let's Encrypt）

```bash
# 第一次签发
docker compose --env-file .env run --rm --service-ports certbot \
    certonly --webroot -w /var/www/certbot \
    -d finhub.tax -d www.finhub.tax \
    --email YOUR_EMAIL@example.com --agree-tos --no-eff-email

# 证书会写到命名卷 certbot-certs:/etc/letsencrypt
```

签发成功后，把 `nginx/conf.d/finhub.tax.conf` 替换为 `nginx/conf.d/finhub.tax-https.conf`（或直接在原文件追加 HTTPS server 块，listen 443 ssl + ssl_certificate 指向 `/etc/letsencrypt/live/finhub.tax/fullchain.pem`），然后：

```bash
docker compose --env-file .env restart nginx
```

证书续签（90 天到期）：
```bash
docker compose --env-file .env run --rm certbot renew
docker compose --env-file .env exec nginx nginx -s reload
```

## 6. 切换到 `real` 集成模式

确认 HTTPS 可用 + 企微后台「可信域名」「网页授权」白名单已配 `finhub.tax`：

```bash
sed -i 's/INTEGRATION_WECOM_AUTH_MODE=mock/INTEGRATION_WECOM_AUTH_MODE=real/' .env
sed -i 's/INTEGRATION_WECHAT_MINIAPP_AUTH_MODE=mock/INTEGRATION_WECHAT_MINIAPP_AUTH_MODE=real/' .env
docker compose --env-file .env restart backend
```

打开 `https://finhub.tax/admin/`，点「使用企业微信登录」→ 应跳企微授权 → 回跳 dashboard。

## 7. 常用命令

```bash
# 升级（git pull + 重新构建 + 重启）
git pull
./scripts/deploy.sh

# 备份数据
docker compose --env-file .env exec mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" wecom_bft_new > backup-$(date +%F).sql

# 完全重置（删数据，慎用）
docker compose --env-file .env down -v

# 进 mysql 客户端
docker compose --env-file .env exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" wecom_bft_new
```

## 已知限制

- 当前 `frontend.Dockerfile` 只构建 admin。Phase B 增加 wecom-sidebar / supplier / lead 后会扩。
- 微信支付仍是 mock，演示时调起 `wx.requestPayment` 会因 `prepay_id` 非法报错（预期）。
- 电子发票仍是 mock。
- 备份策略未自动化；演示前手动跑一次即可。
