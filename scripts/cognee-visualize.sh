#!/bin/bash
# ============================================================
# Cognee 知识图谱可视化 — 生成交互式 HTML
# ============================================================
# 用法:
#   ./scripts/cognee-visualize.sh                 # 输出到 ~/cognee-graph.html
#   ./scripts/cognee-visualize.sh /path/to/output.html
# ============================================================
set -euo pipefail

OUTPUT="${1:-$HOME/cognee-graph.html}"

echo "=== Cognee 知识图谱可视化 ==="
echo "输出文件: $OUTPUT"
echo ""

# 如果 cognee API 服务正在运行，自动添加测试数据
if curl -sf http://localhost:8000/health > /dev/null 2>&1; then
    echo "检测到 cognee 服务正在运行，添加测试数据..."
    
    # 从最近的对话日志中提取内容，或者添加示例数据
    if [ -f /tmp/kchat-backend.log ]; then
        echo "从后端日志提取最近的对话..."
        grep -oP '"content":"[^"]+"' /tmp/kchat-backend.log 2>/dev/null | tail -5 | while read -r line; do
            content=$(echo "$line" | sed 's/"content":"//;s/"//')
            [ -n "$content" ] && curl -s -X POST http://localhost:8000/add \
                -H "Content-Type: application/json" \
                -d "{\"content\": \"$content\"}" > /dev/null
        done
        echo "  测试数据已添加"
    fi
fi

# 执行可视化
python3 << 'PYEOF'
import asyncio, os, sys

os.environ['LLM_API_KEY'] = os.environ.get('LLM_API_KEY', 'ollama')
os.environ['LLM_MODEL'] = os.environ.get('LLM_MODEL', 'ollama/llama3')
os.environ['COGNEE_CACHING'] = 'false'
os.environ['ENABLE_BACKEND_ACCESS_CONTROL'] = 'false'
os.environ['COGNEE_DISABLE_TELEMETRY'] = 'true'

output = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/cognee-graph.html')

async def run():
    import cognee
    print("正在生成知识图谱...")
    await cognee.visualize(output)
    size = os.path.getsize(output)
    print(f"\n✅ 已生成: {output} ({size:,} bytes)")
    print(f"   用浏览器打开即可查看交互式知识图谱")

asyncio.run(run())
PYEOF "$OUTPUT"