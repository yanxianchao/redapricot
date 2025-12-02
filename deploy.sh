#!/bin/bash

# 部署脚本：更新代码，打包应用，重启程序

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的信息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查命令执行结果
check_result() {
    if [ $? -ne 0 ]; then
        print_error "$1"
        exit 1
    fi
}

# 进入项目目录
cd "$(dirname "$0")"
print_info "当前目录: $(pwd)"

# 1. 更新代码
print_info "正在更新代码..."
git pull origin master
check_result "代码更新失败"

# 2. 停止正在运行的应用
print_info "正在停止现有应用..."
if pgrep -f "redapricot" > /dev/null; then
    pkill -f "redapricot"
    print_info "已停止现有应用进程"
else
    print_warn "未找到正在运行的应用进程"
fi

# 等待进程完全退出
sleep 2

# 3. 打包应用
print_info "正在打包应用..."
./gradlew clean build installDist -x test
check_result "应用打包失败"

# 4. 启动应用
print_info "正在启动应用..."
nohup ./build/install/redapricot/bin/redapricot > app.log 2>&1 &

# 等待几秒钟让应用启动
sleep 5

# 5. 检查应用是否成功启动
if pgrep -f "redapricot" > /dev/null; then
    print_info "应用已成功启动"
    print_info "查看日志请使用: tail -f app.log"
else
    print_error "应用启动失败，请检查日志: tail -f app.log"
    exit 1
fi

print_info "部署完成!"