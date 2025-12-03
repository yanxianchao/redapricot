#!/bin/bash

# 简化的打包启动脚本

# 设置颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 进入项目目录
cd "$(dirname "$0")"

# 1. 停止现有应用
print_info "停止现有应用..."
pkill -f "redapricot" || true
sleep 2

# 2. 打包应用
print_info "打包应用..."
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    print_error "打包失败"
    exit 1
fi

# 3. 启动应用
print_info "启动优化版应用..."

JAVA_OPTS="-server \
-Xms1g \
-Xmx4g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=16m \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-XX:+UseCompressedOops \
-XX:+UseCompressedClassPointers \
-Djava.net.preferIPv4Stack=true \
-Dio.netty.allocator.type=pooled \
-Dio.netty.recycler.maxCapacityPerThread=4096 \
-Dio.netty.recycler.ratio=8 \
-Dfile.encoding=UTF-8 \
-Duser.timezone=Asia/Tokyo \
-Dsun.net.inetaddr.ttl=60 \
-Dsun.net.inetaddr.negative.ttl=10 \
-Djava.security.egd=file:/dev/./urandom"

nohup java $JAVA_OPTS -jar build/libs/redapricot-1.0.0.jar > app.log 2>&1 &

sleep 3

# 4. 检查启动状态
if pgrep -f "redapricot" > /dev/null; then
    print_info "应用启动成功！"
    print_info "查看日志: tail -f app.log"
else
    print_error "应用启动失败，请检查日志"
    exit 1
fi