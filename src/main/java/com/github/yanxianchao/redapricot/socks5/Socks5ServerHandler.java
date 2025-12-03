package com.github.yanxianchao.redapricot.socks5;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static io.netty.handler.codec.socksx.v5.Socks5CommandType.CONNECT;
import static io.netty.handler.codec.socksx.v5.Socks5CommandType.UDP_ASSOCIATE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Socks5ServerHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {
    private static final Logger logger = LoggerFactory.getLogger(Socks5ServerHandler.class);
    
    // 全局连接管理器
    private static volatile OptimizedConnectionManager connectionManager;
    private static volatile ScheduledExecutorService cleanupExecutor;
    
    static {
        // 初始化连接管理器和清理任务
        if (connectionManager == null) {
            synchronized (Socks5ServerHandler.class) {
                if (connectionManager == null) {
                    connectionManager = new OptimizedConnectionManager(new io.netty.channel.nio.NioEventLoopGroup(32));
                    cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
                    // 每5分钟清理一次过期连接
                    cleanupExecutor.scheduleAtFixedRate(() -> {
                        try {
                            connectionManager.cleanup();
                        } catch (Exception e) {
                            logger.error("清理连接池时发生错误", e);
                        }
                    }, 5, 5, TimeUnit.MINUTES);
                }
            }
        }
    }

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

        // 使用优化的连接管理器
        Future<Channel> connectionFuture = connectionManager.getOrCreateConnection(request.dstAddr(), request.dstPort());
        connectionFuture.addListener((Future<Channel> f) -> {
            if (f.isSuccess()) {
                Channel targetChannel = f.getNow();
                logger.info("成功连接到目标服务器: {}:{}", request.dstAddr(), request.dstPort());
                
                // 发送成功响应 - 使用正确的目标地址
                Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort()
                );
                ctx.writeAndFlush(response);

                // 为目标服务器通道添加RelayHandler，指向客户端通道
                targetChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                
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
