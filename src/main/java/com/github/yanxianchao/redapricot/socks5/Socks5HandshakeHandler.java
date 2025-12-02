package com.github.yanxianchao.redapricot.socks5;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * SOCKS5初始握手处理器
 * 只处理无认证(NO_AUTH)方式
 */
public class Socks5HandshakeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(Socks5HandshakeHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks5InitialRequest) {
            handleInitialRequest(ctx, (Socks5InitialRequest) msg);
        } else if (msg instanceof DefaultSocks5PasswordAuthRequest) {
            // 我们不支持密码认证，关闭连接
            logger.warn("收到密码认证请求，但我们只支持无认证，关闭连接");
            ctx.close();
        } else {
            // 其他消息传给下一个处理器
            super.channelRead(ctx, msg);
        }
    }

    private void handleInitialRequest(ChannelHandlerContext ctx, Socks5InitialRequest request) {
        logger.info("收到SOCKS5初始握手请求 - 版本: {}, 提供的方法: {}", request.version(), request.authMethods().size());

        // 检查客户端是否支持无认证
        if (!request.authMethods().contains(Socks5AuthMethod.NO_AUTH)) {
            logger.warn("客户端不支持无认证方法");

            // 必须发送NO_ACCEPTABLE_METHOD响应，然后关闭连接
            Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.valueOf((byte) 0xFF));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            ctx.close();
            return;
        }

        logger.info("客户端支持无认证，选择NO_AUTH方法");

        // 发送无认证握手响应
        Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
        ctx.writeAndFlush(response);

        logger.info("SOCKS5无认证握手完成，添加命令处理器");

        // 添加命令解码器（Netty内置，只解析数据包）
        ctx.pipeline().addAfter(ctx.name(), "SOCKS5_COMMAND_DECODER", new Socks5CommandRequestDecoder());

        // 添加命令处理器（处理CONNECT等）
        ctx.pipeline().addAfter("SOCKS5_COMMAND_DECODER", "SOCKS5_COMMAND_HANDLER", new Socks5ServerHandler());

        // 现在移除自身，握手过程结束
        ctx.pipeline().remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            // 网络 IO异常通常是可恢复的，记录debug级别日志
            logger.debug("SOCKS5握手网络异常: {}", cause.getMessage());
        } else {
            // 其他异常记录error级别
            logger.error("SOCKS5握手处理器发生异常", cause);
        }
        ctx.close();
    }
}
