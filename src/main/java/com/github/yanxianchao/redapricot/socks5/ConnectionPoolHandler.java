package com.github.yanxianchao.redapricot.socks5;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接池管理处理器
 * 负责管理连接池中的连接生命周期
 */
public class ConnectionPoolHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolHandler.class);
    
    private final String poolKey;
    private final ConcurrentHashMap<String, io.netty.channel.Channel> connectionPool;
    
    public ConnectionPoolHandler(String poolKey, ConcurrentHashMap<String, io.netty.channel.Channel> connectionPool) {
        this.poolKey = poolKey;
        this.connectionPool = connectionPool;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.debug("连接池连接激活: {}", poolKey);
        ctx.fireChannelActive();
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.debug("连接池连接断开: {}", poolKey);
        // 从连接池中移除断开的连接
        connectionPool.remove(poolKey);
        ctx.fireChannelInactive();
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                logger.debug("连接空闲超时，关闭连接: {}", poolKey);
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("连接池连接异常: {}", poolKey, cause);
        connectionPool.remove(poolKey);
        ctx.close();
    }
}