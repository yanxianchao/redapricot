package com.github.yanxianchao.redapricot.socks5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据中继处理器
 * 负责在客户端和目标服务器之间双向传输数据
 */
public class RelayHandler extends ChannelInboundHandlerAdapter implements ChannelHandler {
    private static final Logger logger = LoggerFactory.getLogger(RelayHandler.class);

    private final Channel relayChannel;
    private volatile boolean isRelayActive = true;

    public RelayHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!isRelayActive) {
            // 如果中继已关闭，释放消息
            ReferenceCountUtil.release(msg);
            return;
        }

        // 将接收到的数据转发到对端
        if (relayChannel.isActive()) {
            ChannelFuture future = relayChannel.writeAndFlush(msg);
            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    logger.debug("数据转发到对端失败: {}", f.cause().getMessage());
                    closeOnFlush(ctx.channel());
                }
            });
        } else {
            // 如果对端通道不活跃，释放消息并关闭当前通道
            ReferenceCountUtil.release(msg);
            logger.warn("对端通道不活跃，关闭当前连接,source={},target={}", ctx.channel().remoteAddress(), relayChannel.remoteAddress());
            closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("通道变为不活跃状态，关闭对端连接");
        isRelayActive = false;
        closeOnFlush(relayChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.net.SocketException && cause.getMessage() != null && 
            (cause.getMessage().contains("Connection reset") || 
             cause.getMessage().contains("Connection closed"))) {
            // 连接重置是常见情况，使用debug级别日志
            logger.debug("连接重置: {}", ctx.channel().remoteAddress());
        } else {
            logger.error("数据中继处理器发生异常", cause);
        }
        isRelayActive = false;
        closeOnFlush(ctx.channel());
        closeOnFlush(relayChannel);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        try {
            logger.debug("SOCKS5中继连接已激活 - 远程地址: {}", ctx.channel().remoteAddress());
            super.channelActive(ctx);
        } catch (Exception e) {
            logger.error("激活通道时发生异常", e);
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        try {
            logger.debug("SOCKS5中继连接已注销 - 远程地址: {}", ctx.channel().remoteAddress());
            isRelayActive = false;
            if (relayChannel != null && relayChannel.isActive()) {
                closeOnFlush(relayChannel);
            }
            super.channelUnregistered(ctx);
        } catch (Exception e) {
            logger.error("注销通道时发生异常", e);
        }
    }

    /**
     * 优雅地关闭通道
     */
    private void closeOnFlush(Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
