#!/bin/bash
# ============================================================
# Cognee 记忆服务 — 启动脚本
# ============================================================
# 启动 Cognee REST API 服务器 (FastAPI :8000)
#
# 用法:
#   ./scripts/start-cognee.sh                    # 前台运行
#   ./scripts/start.sh --with-cognee             # 与其他服务一同启动
#
# 端点:
#   POST /add     — 添加内容到知识图谱
#   POST /search  — 搜索记忆
#   GET  /health  — 健康检查
#
# 环境变量:
#   COGNEE_PORT   — 端口 (默认: 8000)
# ============================================================

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

PORT="${COGNEE_PORT:-8000}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── 加载环境变量 ──────────────────────────────────────────
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a; source "$PROJECT_DIR/.env"; set +a
fi
API_SERVER="$SCRIPT_DIR/cognee-api-server.py"
PID_FILE="/tmp/kchat-cognee.pid"

# ── 检查依赖 ──────────────────────────────────────────────────
command -v python3 &>/dev/null || { echo -e "${RED}[ERROR]${NC} 需要 Python 3"; exit 1; }
[ -f "$API_SERVER" ] || { echo -e "${RED}[ERROR]${NC} 找不到 $API_SERVER"; exit 1; }

python3 -c "import cognee" 2>/dev/null || {
    echo -e "${YELLOW}[WARN]${NC} cognee 未安装，尝试安装..."
    pip3 install cognee 2>&1 | tail -1
    python3 -c "import cognee" 2>/dev/null || {
        echo -e "${RED}[ERROR]${NC} 安装失败，请手动执行: pip3 install cognee"
        exit 1
    }
}

# ── 检查端口是否可用 ──────────────────────────────────────────
if lsof -ti:"$PORT" &>/dev/null; then
    EXISTING_PID=$(lsof -ti:"$PORT")
    echo -e "${YELLOW}[WARN]${NC} 端口 $PORT 已被 PID $EXISTING_PID 占用，正在释放..."
    kill -9 "$EXISTING_PID" 2>/dev/null || true
    sleep 1
fi

# ── 启动服务器 ────────────────────────────────────────────────
# 使用 nohup 启动为后台进程
export COGNEE_PORT="$PORT"
export LLM_API_KEY="${LLM_API_KEY:-ollama}"
export LLM_MODEL="${LLM_MODEL:-ollama/llama3}"
export EMBEDDING_ENDPOINT="${EMBEDDING_ENDPOINT:-http://localhost:11434/v1}"
export EMBEDDING_MODEL="${EMBEDDING_MODEL:-llama3}"
export EMBEDDING_DIMENSIONS="${EMBEDDING_DIMENSIONS:-4096}"
nohup python3 "$API_SERVER" > /tmp/kchat-cognee.log 2>&1 &
COGNEE_PID=$!
echo "$COGNEE_PID" > "$PID_FILE"

echo -e "${CYAN}[INFO]${NC}  Cognee 服务启动中 (PID: $COGNEE_PID, :$PORT)"

# ── 等待就绪 ──────────────────────────────────────────────────
for i in $(seq 1 30); do
    if curl -sf "http://localhost:$PORT/health" > /dev/null 2>&1; then
        echo -e "${GREEN}[ OK ]${NC}  Cognee 已就绪"
        echo -e "  ${CYAN}添加记忆:${NC}  curl -X POST http://localhost:$PORT/add -H 'Content-Type: application/json' -d '{\"content\":\"...\"}'"
        echo -e "  ${CYAN}搜索记忆:${NC}  curl -X POST http://localhost:$PORT/search -H 'Content-Type: application/json' -d '{\"query\":\"...\"}'"
        echo -e "  ${CYAN}查看日志:${NC}  tail -f /tmp/kchat-cognee.log"
        exit 0
    fi
    sleep 1
done

echo -e "${RED}[ERROR]${NC} Cognee 启动超时，请检查日志: tail -f /tmp/kchat-cognee.log"
exit 1
