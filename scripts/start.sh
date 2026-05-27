#!/bin/bash

# KChat 一键启动脚本

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}       KChat 一键启动脚本${NC}"
echo -e "${GREEN}========================================${NC}"

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "\n${YELLOW}[1/3] 停止现有服务...${NC}"
# 停止现有服务
lsof -ti:8080 | xargs kill -9 2>/dev/null
lsof -ti:5173 | xargs kill -9 2>/dev/null
sleep 1
echo -e "${GREEN}✓ 已停止现有服务${NC}"

echo -e "\n${YELLOW}[2/3] 启动后端服务...${NC}"
# 设置 Java Home
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

# 启动后端（后台运行）
cd "$PROJECT_DIR/backend"
nohup mvn spring-boot:run > /tmp/kchat-backend.log 2>&1 &
BACKEND_PID=$!
echo -e "${GREEN}✓ 后端服务启动中 (PID: $BACKEND_PID)${NC}"

# 等待后端启动
echo -e "${YELLOW}等待后端服务启动...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8080/api/models > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 后端服务已就绪 (http://localhost:8080)${NC}"
        break
    fi
    sleep 1
done

echo -e "\n${YELLOW}[3/3] 启动前端服务...${NC}"
# 启动前端（后台运行）
cd "$PROJECT_DIR/frontend"
nohup npm run dev > /tmp/kchat-frontend.log 2>&1 &
FRONTEND_PID=$!
echo -e "${GREEN}✓ 前端服务启动中 (PID: $FRONTEND_PID)${NC}"

# 等待前端启动
sleep 3

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}       启动完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${GREEN}前端地址:${NC} http://localhost:5173"
echo -e "${GREEN}后端地址:${NC} http://localhost:8080"
echo -e "\n${YELLOW}查看后端日志:${NC} tail -f /tmp/kchat-backend.log"
echo -e "${YELLOW}查看前端日志:${NC} tail -f /tmp/kchat-frontend.log"
echo -e "${YELLOW}停止服务:${NC} kill $BACKEND_PID $FRONTEND_PID"
echo ""
