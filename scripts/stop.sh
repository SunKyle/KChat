#!/bin/bash
# ============================================================
# KChat 停止脚本
# ============================================================
# 停止后端 (:8080)、前端 (:5173)、Cognee (:8000)
#
# 用法:
#   ./scripts/stop.sh
# ============================================================

set -euo pipefail

GREEN='\033[0;32m'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── 加载环境变量 ──────────────────────────────────────────
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a; source "$PROJECT_DIR/.env"; set +a
fi
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}━━━ 停止 KChat 服务 ━━━${NC}"

stopped=0
for port in 8080 5173 ${COGNEE_PORT:-8000}; do
    name=""
    case "$port" in
        8080) name="后端 (Spring Boot)" ;;
        5173) name="前端 (Vite)" ;;
        8000) name="Cognee 记忆服务" ;;
    esac

    PID=$(lsof -ti:"$port" 2>/dev/null || true)
    if [ -n "$PID" ]; then
        kill -9 "$PID" 2>/dev/null || true
        echo -e "${GREEN}  ✓${NC} 端口 $port ($name) PID $PID 已停止"
        stopped=$((stopped + 1))
    else
        echo -e "  ${YELLOW}  -${NC} 端口 $port ($name) 未运行"
    fi
done

sleep 1

if [ "$stopped" -gt 0 ]; then
    echo -e "\n${GREEN}✓ 已停止 $stopped 个服务${NC}"
else
    echo -e "\n${YELLOW}没有正在运行的服务${NC}"
fi
