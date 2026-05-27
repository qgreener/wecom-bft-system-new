#!/usr/bin/env bash
# =============================================================================
# Phase E 一键部署脚本（在服务器上执行）
# 用法：
#   cd /opt/wecom-bft-system/deploy
#   ./scripts/deploy.sh                # 全量构建 + 拉起
#   ./scripts/deploy.sh --pull         # 仅拉镜像、重启（不重新 build）
# =============================================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DEPLOY_DIR"

if [[ ! -f .env ]]; then
    echo "❌ deploy/.env 不存在。请先：cp .env.production.example .env 并填入真实凭据"
    exit 1
fi

MODE="${1:-build}"

case "$MODE" in
    --pull)
        echo ">>> Pulling base images (skip build)"
        docker compose --env-file .env pull
        ;;
    *)
        echo ">>> Building images"
        docker compose --env-file .env build
        ;;
esac

echo ">>> Starting services"
docker compose --env-file .env up -d

echo ">>> Waiting 20s for backend to become healthy..."
sleep 20

echo ">>> Service status:"
docker compose --env-file .env ps

echo ">>> Backend health probe:"
if docker compose --env-file .env exec -T backend wget -q -O- http://127.0.0.1:8080/api/health 2>/dev/null; then
    echo ""
    echo "✅ Deploy succeeded. Visit: http://finhub.tax/admin/"
else
    echo ""
    echo "⚠️  Backend not yet healthy. Tail logs with: docker compose logs -f backend"
fi
