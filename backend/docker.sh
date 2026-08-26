#!/bin/bash
set -e
# ============================================================
# Spring Security Auth Lab — 6 个业务服务构建 + 部署
# MySQL / Redis / Nacos 由用户自行部署
# 用法: ./docker.sh
# ============================================================
cd "$(dirname "$0")"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
step()  { echo -e "\n${BLUE}========== $1 ==========${NC}"; }

# ============================================================
# 1. 构建后端
# ============================================================
step "构建后端"

info "Building session-auth..."
(cd backend/session-auth && mvn clean package -DskipTests -Pdev)

info "Building jwt-auth..."
(cd backend/jwt-auth && mvn clean package -DskipTests -Pdev)

info "Building opaque-auth..."
(cd backend/opaque-auth && mvn clean package -DskipTests -Pdev)

info "Building oauth2-auth/auth-server..."
(cd backend/oauth2-auth/auth-server && mvn clean package -DskipTests -Pdev)

info "Building oauth2-auth/resource-server..."
(cd backend/oauth2-auth/resource-server && mvn clean package -DskipTests -Pdev)

info "Building oauth2-auth/client-server..."
(cd backend/oauth2-auth/client-server && mvn clean package -DskipTests -Pdev)

# ============================================================
# 2. 清理旧容器
# ============================================================
step "清理旧容器"
for c in lab-session-auth lab-jwt-auth lab-opaque-auth \
         lab-oauth2-auth-server lab-oauth2-resource-server lab-oauth2-client-server; do
  docker rm -f "$c" 2>/dev/null && info "  removed $c" || true
done

# ============================================================
# 3. Docker Compose 部署
# ============================================================
step "Docker Compose 部署"
docker compose up -d --build

echo ""
step "===== 全部部署完成 ====="
info "6 个业务服务已启动，通过 Gateway :18090 统一访问"
