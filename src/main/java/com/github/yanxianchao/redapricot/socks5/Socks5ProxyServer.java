package com.github.yanxianchao.redapricot.socks5;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * SOCKS5 代理服务器
 */
public class Socks5ProxyServer {
    private static final Logger logger = LoggerFactory.getLogger(Socks5ProxyServer.class);

    private final String host = "0.0.0.0";
    private final int port = 1080;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile boolean running = false;

    public void run() throws Exception {
        logger.info("准备启动SOCKS5代理服务器，绑定地址: {}:{}", host, port);
        this.start();
    }


    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(8);
        workerGroup = new NioEventLoopGroup(64);

        try {

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, 256 * 1024)
                    .childOption(ChannelOption.SO_SNDBUF, 256 * 1024)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                            new io.netty.channel.WriteBufferWaterMark(128 * 1024, 512 * 1024))
                    .childOption(ChannelOption.SO_LINGER, 0)
                    .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .childOption(ChannelOption.AUTO_READ, true)
                    .childOption(ChannelOption.MAX_MESSAGES_PER_READ, 16)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // Debug 日志
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                            // SOCKS协议统一处理器
                            ch.pipeline().addLast(new SocksPortUnificationServerHandler());
                            // SOCKS5编码器
                            //ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT);
                            // SOCKS5握手处理器（处理握手逻辑）
                            ch.pipeline().addLast(new Socks5HandshakeHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(host, port).sync();
            running = true;
            logger.info("SOCKS5代理服务器已启动，监听地址: {}", future.channel().localAddress());
            // 等待服务器绑定端口完成
            future.channel().closeFuture().sync();
        } finally {
            running = false;
            stop();
        }
    }

    public void stop() {
        if (!running) {
            return;
        }

        logger.info("正在关闭SOCKS5代理服务器...");

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        logger.info("SOCKS5代理服务器已关闭");
    }

    public boolean isRunning() {
        return running;
    }
}
