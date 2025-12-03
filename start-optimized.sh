#!/bin/bash

# 优化JVM参数的启动脚本
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

# 启动应用
java $JAVA_OPTS -jar build/libs/redapricot-1.0.0.jar