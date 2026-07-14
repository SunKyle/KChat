#!/bin/bash
# ============================================================
# Cognee 知识图谱可视化
# ============================================================
# 生成交互式知识图谱 HTML 文件，浏览器打开即可查看
#
# 用法:
#   DEEPSEEK_API_KEY=sk-xxx ./scripts/cognee-graph.sh
#   DEEPSEEK_API_KEY=sk-xxx ./scripts/cognee-graph.sh ~/my-graph.html
#
# 输出: ~/cognee-graph.html（可指定路径参数覆盖）
# ============================================================
set -euo pipefail

OUTPUT="${1:-$HOME/cognee-graph.html}"
KEY="${LLM_API_KEY:-${DEEPSEEK_API_KEY:-}}"
RESTART_COGNEE=false

if [ -z "$KEY" ]; then
    echo "错误: 需要设置 DEEPSEEK_API_KEY 环境变量"
    echo "用法: DEEPSEEK_API_KEY=sk-xxx $0 [输出路径]"
    exit 1
fi

echo "=== Cognee 知识图谱可视化 ==="
echo "输出: $OUTPUT"

# 如果 cognee 服务在运行，先停止（数据库锁冲突）
COGNEE_PID=$(lsof -ti:8000 2>/dev/null || true)
if [ -n "$COGNEE_PID" ]; then
    echo "检测到 cognee 服务 (PID $COGNEE_PID)，停止以释放数据库锁..."
    kill -9 "$COGNEE_PID" 2>/dev/null || true
    sleep 2
    RESTART_COGNEE=true
    echo "  已停止"
fi

echo ""

# 通过环境变量传递
export LLM_API_KEY="$KEY"
export LLM_MODEL="deepseek/deepseek-chat"
export COGNEE_CACHING="false"
export ENABLE_BACKEND_ACCESS_CONTROL="false"
export COGNEE_DISABLE_TELEMETRY="true"
export OUTPUT_PATH="$OUTPUT"

python3 << 'PYEOF'
import asyncio, os

output = os.environ.get('OUTPUT_PATH', os.path.expanduser('~/cognee-graph.html'))
os.environ.setdefault('LLM_MODEL', 'deepseek/deepseek-chat')
os.environ.setdefault('COGNEE_CACHING', 'false')
os.environ.setdefault('ENABLE_BACKEND_ACCESS_CONTROL', 'false')
os.environ.setdefault('COGNEE_DISABLE_TELEMETRY', 'true')

async def run():
    import cognee
    print("正在生成知识图谱（需联网调用 LLM 提取实体关系，约 5-15 秒）...")
    await cognee.visualize(output)
    size = os.path.getsize(output)
    print(f"\n✅ 已生成: {output} ({size:,} 字节)")
    print(f"   用浏览器打开即可查看交互式知识图谱")

asyncio.run(run())
PYEOF

# 如果之前停了 cognee 服务，询问是否重启
if [ "$RESTART_COGNEE" = true ]; then
    echo ""
    echo "cognee 服务之前已被停止。如需使用，请重新启动:"
    echo "  ./scripts/start.sh --deepseek"
    echo "  # 或仅重启 cognee:"
    echo "  cd $(dirname "${BASH_SOURCE[0]}") && bash start-cognee.sh"
fi
