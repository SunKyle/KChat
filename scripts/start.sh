#!/bin/bash
# ============================================================
# KChat 一键启动脚本
# ============================================================
# 启动后端 (Spring Boot :8080)、前端 (Vite :5173)，
# 可选启动 Cognee 记忆服务 (FastAPI :8000)
#
# 用法:
#   ./scripts/start.sh                 # 仅启动后端 + 前端
#   ./scripts/start.sh --with-cognee   # 启动全部三个服务
#   ./scripts/start.sh --cognee-only   # 仅启动 Cognee
#
# 停止:
#   ./scripts/stop.sh
# ============================================================

set -euo pipefail

# ── 颜色 & 格式化 ─────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[ OK ]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }
step()  { echo -e "\n${BOLD}${BLUE}━━━ $* ━━━${NC}"; }
check() { printf "  %-30s" "$1"; }

# ── 路径 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── 加载环境变量 ──────────────────────────────────────────
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a; source "$PROJECT_DIR/.env"; set +a
fi
COGNEE_SERVER="$SCRIPT_DIR/cognee-api-server.py"

# ── 参数解析 ──────────────────────────────────────────────────
START_COGNEE=false
COGNEE_ONLY=false
for arg in "$@"; do
    case "$arg" in
        --with-cognee) START_COGNEE=true ;;
        --cognee-only) COGNEE_ONLY=true ;;
        --deepseek)
            START_COGNEE=true
            KEY="${LLM_API_KEY:-${DEEPSEEK_API_KEY:-}}"
            if [ -z "$KEY" ]; then
                echo -e "${RED}[ERROR]${NC} --deepseek 需要设置 LLM_API_KEY 或 DEEPSEEK_API_KEY"
                echo "  例如: DEEPSEEK_API_KEY=sk-xxx $0 --deepseek"
                exit 1
            fi
            export LLM_API_KEY="$KEY"
            export LLM_MODEL="${LLM_MODEL:-deepseek/deepseek-chat}"
            ;;
        --help|-h)
            echo "用法: $0 [--with-cognee] [--cognee-only] [--deepseek]"
            echo "  --with-cognee   同时启动 Cognee 记忆服务"
            echo "  --cognee-only   仅启动 Cognee 记忆服务"
            echo "  --deepseek      使用 DeepSeek 替代 Ollama 作为 Cognee 的 LLM"
            exit 0
            ;;
    esac
done

# ── 检查工具链 ────────────────────────────────────────────────
step "检查依赖"

check "Java 21+" && command -v java &>/dev/null && java -version 2>&1 | head -1 && ok "java 就绪" || { err "需要 Java 21+"; exit 1; }
check "Maven"     && command -v mvn  &>/dev/null && mvn --version 2>&1 | head -1 | awk '{print "  "$0}' && ok "mvn 就绪"   || { err "需要 Maven"; exit 1; }
check "Node.js"   && command -v node &>/dev/null && node --version && ok "node 就绪"   || { err "需要 Node.js"; exit 1; }
check "npm"       && command -v npm  &>/dev/null && npm --version  && ok "npm 就绪"    || { err "需要 npm"; exit 1; }

if $START_COGNEE || $COGNEE_ONLY; then
    check "Python 3"  && command -v python3 &>/dev/null && python3 --version 2>&1 && ok "python3 就绪" || { err "需要 Python 3"; exit 1; }
    check "Cognee"    && python3 -c "import cognee; print(f'v{cognee.__version__}')" 2>&1 && ok "cognee 就绪" || {
        warn "cognee 未安装，尝试自动安装..."
        pip3 install cognee 2>&1 | tail -1
        python3 -c "import cognee; print(f'v{cognee.__version__}')" 2>&1 && ok "cognee 安装完成" || { err "cognee 安装失败，请手动执行: pip3 install cognee"; exit 1; }
    }
fi

# ── 停止旧服务 ────────────────────────────────────────────────
step "停止旧服务"

# 根据端口杀掉进程及其子进程
kill_port() {
    local port="$1"
    local pids
    pids=$(lsof -ti:"$port" 2>/dev/null || true)
    if [ -z "$pids" ]; then
        return 0
    fi

    # 第一轮: SIGTERM 优雅关闭
    for pid in $pids; do
        kill -15 "$pid" 2>/dev/null || true
        # 同时终止子进程
        pkill -P "$pid" 2>/dev/null || true
    done
    sleep 1

    # 第二轮: 检查是否还有存活进程
    local remaining
    remaining=$(lsof -ti:"$port" 2>/dev/null || true)
    if [ -n "$remaining" ]; then
        for pid in $remaining; do
            kill -9 "$pid" 2>/dev/null || true
            pkill -P "$pid" 2>/dev/null || true
        done
        sleep 0.5
    fi
}

for port in 8080 5173 8000; do
    if lsof -ti:"$port" &>/dev/null; then
        kill_port "$port"
        ok "端口 $port 已释放"
    else
        ok "端口 $port 空闲"
    fi
done

# ── 仅启动 Cognee ─────────────────────────────────────────────
if $COGNEE_ONLY; then
    step "启动 Cognee 记忆服务"
    exec bash "$SCRIPT_DIR/start-cognee.sh"
fi

# ── 启动后端 (Spring Boot) ────────────────────────────────────
step "启动后端服务 (Spring Boot :8080)"

# 动态查找 Java 21+ 环境
JAVA_BIN="$(command -v java)"
if [ -z "$JAVA_BIN" ]; then
    err "找不到 java 命令，请安装 JDK 21+"
    exit 1
fi

# ── 策略 1: macOS 专用 ── 使用 java_home 查找最高版本 JDK ──
if command -v /usr/libexec/java_home &>/dev/null; then
    # 先尝试找已注册的 JDK 21+
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null \
             || /usr/libexec/java_home -v 25 2>/dev/null \
             || /usr/libexec/java_home -v 22 2>/dev/null \
             || /usr/libexec/java_home -v 23 2>/dev/null \
             || /usr/libexec/java_home 2>/dev/null \
             || true)

    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        # 验证版本
        JAVA_VER="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
        JAVA_MAJOR="$(echo "$JAVA_VER" | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
        if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -ge 21 ] 2>/dev/null; then
            export JAVA_HOME
            export PATH="$JAVA_HOME/bin:$PATH"
            info "JAVA_HOME=$JAVA_HOME (Java $JAVA_MAJOR)"
        else
            warn "java_home 找到的 JDK 版本 $JAVA_MAJOR 低于 21，尝试其他方式..."
            JAVA_HOME=""
        fi
    fi
fi

# ── 策略 2: 检查 Homebrew 安装的 JDK ──
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in \
        /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/opt/openjdk@22/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/Cellar/openjdk/21.0.1/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; do
        if [ -x "$candidate/bin/java" ]; then
            JAVA_HOME="$candidate"
            export JAVA_HOME
            export PATH="$JAVA_HOME/bin:$PATH"
            info "JAVA_HOME=$JAVA_HOME (Homebrew JDK)"
            break
        fi
    done
fi

# ── 策略 3: 从 java 命令路径反推（仅适用于非 macOS） ──
if [ -z "${JAVA_HOME:-}" ]; then
    # readlink -f 在 macOS 不可用，尝试 realpath (brew install coreutils 后可用)
    if command -v realpath &>/dev/null; then
        REAL_JAVA="$(realpath "$JAVA_BIN")"
    elif readlink -f "$JAVA_BIN" &>/dev/null; then
        REAL_JAVA="$(readlink -f "$JAVA_BIN")"
    else
        REAL_JAVA="$JAVA_BIN"
    fi
    # 如果是 /usr/bin/java (Apple 启动器)，跳过此策略
    if [ "$REAL_JAVA" != "/usr/bin/java" ]; then
        JAVA_HOME="$(dirname "$(dirname "$REAL_JAVA")")"
        if [ -x "$JAVA_HOME/bin/java" ]; then
            export JAVA_HOME
            export PATH="$JAVA_HOME/bin:$PATH"
            info "JAVA_HOME=$JAVA_HOME"
        fi
    fi
fi

# ── 最终验证 ──
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    err "无法找到 JDK 21+，请手动设置 JAVA_HOME 或安装 JDK:"
    echo "  macOS: brew install openjdk@21"
    echo "  或设置环境变量: export JAVA_HOME=/path/to/your/jdk"
    exit 1
fi

cd "$PROJECT_DIR/backend"
# 清空旧日志
> /tmp/kchat-backend.log
info "启动后端: mvn spring-boot:run (JAVA_HOME=$JAVA_HOME)"
nohup mvn spring-boot:run >> /tmp/kchat-backend.log 2>&1 &
BACKEND_PID=$!
echo "  PID: $BACKEND_PID"

# 立即检查进程是否存活
sleep 1
if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    err "Maven 进程已退出！日志:"
    tail -30 /tmp/kchat-backend.log
    exit 1
fi

check "等待后端就绪"
for i in $(seq 1 45); do
    # 早期失败检测: 如果 Maven 进程已退出且日志中有 ERROR
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        if grep -qi "error\|failed\|exception" /tmp/kchat-backend.log 2>/dev/null; then
            err "后端启动失败！日志摘要:"
            tail -20 /tmp/kchat-backend.log
            exit 1
        fi
    fi
    if curl -sf http://localhost:8080/api/models > /dev/null 2>&1; then
        ok "后端已就绪 (http://localhost:8080)"
        break
    fi
    if [ "$i" -eq 45 ]; then
        warn "后端启动超时 (90秒)，请检查: tail -f /tmp/kchat-backend.log"
    fi
    sleep 2
done

# ── 启动 Cognee (可选) ────────────────────────────────────────
if $START_COGNEE; then
    step "启动 Cognee 记忆服务 (FastAPI :8000)"

    if [ ! -f "$COGNEE_SERVER" ]; then
        err "找不到 cognee API 服务器: $COGNEE_SERVER"
        exit 1
    fi

    export COGNEE_PORT="${COGNEE_PORT:-8000}"
    export LLM_API_KEY="${LLM_API_KEY:-ollama}"
    export LLM_MODEL="${LLM_MODEL:-ollama/llama3}"
    export EMBEDDING_ENDPOINT="${EMBEDDING_ENDPOINT:-http://localhost:11434/v1}"
    export EMBEDDING_MODEL="${EMBEDDING_MODEL:-ollama/nomic-embed-text}"
    export EMBEDDING_DIMENSIONS="${EMBEDDING_DIMENSIONS:-768}"
    if [ -n "${LLM_API_KEY:-}" ] && [ "$LLM_API_KEY" != "ollama" ]; then
        export LLM_MODEL="${LLM_MODEL:-deepseek/deepseek-chat}"
    fi
    nohup python3 "$COGNEE_SERVER" > /tmp/kchat-cognee.log 2>&1 &
    COGNEE_PID=$!
    echo "  PID: $COGNEE_PID"

    check "等待 Cognee 就绪"
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8000/health > /dev/null 2>&1; then
            ok "Cognee 已就绪 (http://localhost:8000)"
            break
        fi
        if [ "$i" -eq 30 ]; then
            warn "Cognee 启动超时，请检查: tail -f /tmp/kchat-cognee.log"
        fi
        sleep 1
    done
fi

# ── 启动前端 (Vite) ───────────────────────────────────────────
step "启动前端服务 (Vite :5173)"

cd "$PROJECT_DIR/frontend"
nohup npm run dev > /tmp/kchat-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "  PID: $FRONTEND_PID"

check "等待前端就绪"
for i in $(seq 1 20); do
    if curl -sf http://localhost:5173 > /dev/null 2>&1; then
        ok "前端已就绪 (http://localhost:5173)"
        break
    fi
    if [ "$i" -eq 20 ]; then
        warn "前端启动超时，请检查: tail -f /tmp/kchat-frontend.log"
    fi
    sleep 1
done

# ── 显示启动结果 ──────────────────────────────────────────────
step "启动结果"

echo ""
echo -e "  ${BOLD}服务           地址                        PID${NC}"
echo -e "  ${BOLD}─────          ────                        ───${NC}"
echo -e "  ${CYAN}前端${NC}          http://localhost:5173           $FRONTEND_PID"
echo -e "  ${GREEN}后端${NC}          http://localhost:8080           $BACKEND_PID"

if $START_COGNEE && [ -n "${COGNEE_PID:-}" ]; then
    echo -e "  ${BLUE}Cognee${NC}        http://localhost:8000           $COGNEE_PID"
fi

echo ""
echo -e "  ${YELLOW}日志文件${NC}"
echo -e "    后端:  tail -f /tmp/kchat-backend.log"
echo -e "    前端:  tail -f /tmp/kchat-frontend.log"
if $START_COGNEE && [ -n "${COGNEE_PID:-}" ]; then
    echo -e "    Cognee: tail -f /tmp/kchat-cognee.log"
fi
echo -e "    停止:  ${BOLD}$SCRIPT_DIR/stop.sh${NC}"
echo ""

ok "KChat 启动完成！"
