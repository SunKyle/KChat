#!/bin/bash

# KChat 停止脚本

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}停止 KChat 服务...${NC}"

# 停止后端和前端
lsof -ti:8080 | xargs kill -9 2>/dev/null
lsof -ti:5173 | xargs kill -9 2>/dev/null

echo -e "${GREEN}✓ 所有服务已停止${NC}"
