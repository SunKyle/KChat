#!/bin/bash
# ============================================================
# Cognee 记忆服务 — 启动脚本
# ============================================================
# 启动 Cognee REST API 服务器 (FastAPI :8000)
# LLM/Embedding 配置从 .env 文件读取
#
# 用法:
#   ./scripts/start-cognee.sh                    # 前台运行
#   ./scripts/start.sh --cognee-only             # 通过 start.sh 调用
# ============================================================

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── 加载 .env（唯一配置来源）──────────────────────────────
if [ ! -f "$PROJECT_DIR/.env" ]; then
    echo -e "${RED}[ERROR]${NC} 找不到 .env 文件: $PROJECT_DIR/.env"
    exit 1
fi
set -a; source "$PROJECT_DIR/.env"; set +a

PORT="${COGNEE_PORT:-8000}"
API_SERVER="$SCRIPT_DIR/cognee-api-server.py"
LOG_DIR="$PROJECT_DIR/logs"
PID_FILE="$LOG_DIR/cognee.pid"
mkdir -p "$LOG_DIR"

# ── 查找装有 cognee 的 Python ────────────────────────────────
COGNEE_PYTHON=""
for _py in \
    /Library/Frameworks/Python.framework/Versions/3.13/bin/python3 \
    /Library/Frameworks/Python.framework/Versions/3.12/bin/python3 \
    /usr/local/bin/python3 \
    "$(command -v python3)"; do
    if [ -x "$_py" ] && "$_py" -c "import cognee, fastapi" &>/dev/null; then
        COGNEE_PYTHON="$_py"
        break
    fi
done
if [ -z "$COGNEE_PYTHON" ]; then
    echo -e "${RED}[ERROR]${NC} 找不到装有 cognee + fastapi 的 Python"
    echo "  请安装: pip3 install cognee fastapi uvicorn python-dotenv"
    exit 1
fi
[ -f "$API_SERVER" ] || { echo -e "${RED}[ERROR]${NC} 找不到 $API_SERVER"; exit 1; }

# ── 检查端口是否可用 ──────────────────────────────────────────
EXISTING_PIDS=$(lsof -ti:"$PORT" 2>/dev/null || true)
if [ -n "$EXISTING_PIDS" ]; then
    echo -e "${YELLOW}[WARN]${NC} 端口 $PORT 已被占用，正在释放..."
    for pid in $EXISTING_PIDS; do
        kill -15 "$pid" 2>/dev/null || true
        pkill -P "$pid" 2>/dev/null || true
    done
    sleep 1
    REMAINING=$(lsof -ti:"$PORT" 2>/dev/null || true)
    if [ -n "$REMAINING" ]; then
        for pid in $REMAINING; do
            kill -9 "$pid" 2>/dev/null || true
            pkill -P "$pid" 2>/dev/null || true
        done
        sleep 0.5
    fi
fi

# ── 启动服务器 ────────────────────────────────────────────────
export COGNEE_PORT="$PORT"
nohup "$COGNEE_PYTHON" "$API_SERVER" > "$LOG_DIR/cognee.log" 2>&1 &
COGNEE_PID=$!
echo "$COGNEE_PID" > "$PID_FILE"

echo -e "${CYAN}[INFO]${NC}  Cognee 服务启动中 (PID: $COGNEE_PID, :$PORT)"
echo -e "  ${CYAN}Python:${NC}    $COGNEE_PYTHON"
echo -e "  ${CYAN}LLM:${NC}       ${LLM_MODEL:-未配置}"
echo -e "  ${CYAN}Embedding:${NC} ${EMBEDDING_MODEL:-未配置}"

# ── 等待就绪 ──────────────────────────────────────────────────
for i in $(seq 1 30); do
    if curl -sf "http://localhost:$PORT/health" > /dev/null 2>&1; then
        echo -e "${GREEN}[ OK ]${NC}  Cognee 已就绪"
        echo -e "  ${CYAN}查看日志:${NC}  tail -f $LOG_DIR/cognee.log"
        exit 0
    fi
    sleep 1
done

echo -e "${RED}[ERROR]${NC} Cognee 启动超时，请检查日志: tail -f $LOG_DIR/cognee.log"
exit 1
