package com.github.yanxianchao.redapricot.socks5;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;

import static io.netty.handler.codec.socksx.v5.Socks5CommandType.CONNECT;
import static io.netty.handler.codec.socksx.v5.Socks5CommandType.UDP_ASSOCIATE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Socks5ServerHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {
    private static final Logger logger = LoggerFactory.getLogger(Socks5ServerHandler.class);

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5CommandRequest request) throws Exception {
        Socks5CommandType type = request.type();
        if (type.equals(CONNECT)) {
            handleConnectCommand(ctx, request);
        } else if (type.equals(UDP_ASSOCIATE)) {
            handleUdpAssociateCommand(ctx, request);
        } else {
            logger.warn("不支持的SOCKS5命令类型: {}", request.type());
            ctx.close();
        }
    }

    private void handleConnectCommand(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        logger.info("处理CONNECT命令 - 目标地址: {}:{}", request.dstAddr(), request.dstPort());

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_RCVBUF, 32 * 1024)
                .option(ChannelOption.SO_SNDBUF, 32 * 1024)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        // 为目标服务器通道添加RelayHandler，指向客户端通道
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture future = bootstrap.connect(request.dstAddr(), request.dstPort());
        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                logger.info("成功连接到目标服务器: {}:{}", request.dstAddr(), request.dstPort());
                
                // 发送成功响应 - 使用正确的目标地址
                Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort()
                );
                ctx.writeAndFlush(response);

                // 设置双向数据转发
                Channel targetChannel = f.channel();

                // 为客户端通道添加RelayHandler，指向目标服务器通道（客户端->服务器方向）
                ctx.pipeline().addLast(new RelayHandler(targetChannel));

                logger.info("SOCKS5连接建立，开始双向数据转发");

                // 移除 SOCKS5 协议处理器（保留relay和数据传输）
                if (ctx.pipeline().get("SOCKS5_COMMAND_DECODER") != null) {
                    ctx.pipeline().remove("SOCKS5_COMMAND_DECODER");
                }
                if (ctx.pipeline().get("SOCKS5_COMMAND_HANDLER") != null) {
                    ctx.pipeline().remove("SOCKS5_COMMAND_HANDLER");
                }
            } else {
                logger.error("连接目标服务器失败: {}:{}", request.dstAddr(), request.dstPort(), f.cause());
                
                // 发送失败响应
                Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.FAILURE, 
                    request.dstAddrType()
                );
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    private void handleUdpAssociateCommand(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        logger.info("处理UDP_ASSOCIATE命令");
        
        // 对于UDP关联命令，返回当前服务器的监听地址和端口
        InetSocketAddress localAddr = (InetSocketAddress) ctx.channel().localAddress();
        Socks5CommandResponse response = new DefaultSocks5CommandResponse(
            Socks5CommandStatus.SUCCESS,
            request.dstAddrType(),
            localAddr.getHostString(),
            localAddr.getPort()
        );
        ctx.writeAndFlush(response);
        
        // 移除 SOCKS5 相关的处理器
        if (ctx.pipeline().get("SOCKS5_COMMAND_DECODER") != null) {
            ctx.pipeline().remove("SOCKS5_COMMAND_DECODER");
        }
        if (ctx.pipeline().get("SOCKS5_COMMAND_HANDLER") != null) {
            ctx.pipeline().remove("SOCKS5_COMMAND_HANDLER");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("SOCKS5服务器处理器发生异常", cause);
        ctx.close();
    }
}
