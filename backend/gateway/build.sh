#!/bin/bash
set -e
# ============================================================
# Gateway 统一网关 — 构建 + Docker 部署
# 用法: ./build.sh
# ============================================================
cd "$(dirname "$0")"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
step()  { echo -e "\n${BLUE}========== $1 ==========${NC}"; }

IMAGE="security-gateway:latest"
CONTAINER="security-gateway"

# ============================================================
# 1. 构建 JAR
# ============================================================
step "Maven 构建 Gateway"
info "mvn clean package -DskipTests ..."
mvn clean package -DskipTests

# ============================================================
# 2. 清理旧容器
# ============================================================
step "清理旧容器"
docker rm -f "$CONTAINER" 2>/dev/null && info "  removed $CONTAINER" || true

# ============================================================
# 3. Docker 构建 + 运行
# ============================================================
step "Docker 构建镜像"
docker build -t "$IMAGE" .

step "Docker 启动容器"
docker run -d \
  --name "$CONTAINER" \
  --network host \
  "$IMAGE"

echo ""
step "===== Gateway 部署完成 ====="
info "Gateway 监听 :18090"
info "测试: curl http://localhost:18090/api/jwt/public/hello"
