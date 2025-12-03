#!/bin/bash

# 停止应用程序脚本

# 设置颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 查找并停止redapricot进程
print_info "正在查找RedApricot进程..."

# 获取进程信息
PIDS=$(pgrep -f "redapricot")

if [ -z "$PIDS" ]; then
    print_warn "未找到运行中的RedApricot进程"
    exit 0
fi

print_info "找到以下进程:"
ps -p $PIDS -o pid,ppid,etime,cmd

# 优雅停止进程
print_info "正在优雅停止进程..."
pkill -TERM -f "redapricot"

# 等待进程停止
sleep 3

# 检查是否还有进程在运行
REMAINING_PIDS=$(pgrep -f "redapricot")

if [ -n "$REMAINING_PIDS" ]; then
    print_warn "仍有进程在运行，强制终止..."
    pkill -KILL -f "redapricot"
    sleep 1
fi

# 最终检查
FINAL_PIDS=$(pgrep -f "redapricot")
if [ -z "$FINAL_PIDS" ]; then
    print_info "RedApricot应用程序已成功停止"
else
    print_error "无法停止以下进程:"
    ps -p $FINAL_PIDS -o pid,ppid,etime,cmd
    exit 1
fi