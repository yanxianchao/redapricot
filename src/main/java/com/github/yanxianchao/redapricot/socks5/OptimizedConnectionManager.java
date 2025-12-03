package com.github.yanxianchao.redapricot.socks5;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 优化的连接管理器
 * 提供连接池、DNS缓存和心跳机制
 */
public class OptimizedConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedConnectionManager.class);
    
    private final EventLoopGroup eventLoopGroup;
    private final DnsNameResolver dnsResolver;
    private final ConcurrentHashMap<String, InetAddress> dnsCache;
    private final ConcurrentHashMap<String, Channel> connectionPool;
    private static final long DNS_CACHE_TTL = TimeUnit.MINUTES.toMillis(5);
    private static final long CONNECTION_POOL_TTL = TimeUnit.MINUTES.toMillis(2);
    
    public OptimizedConnectionManager(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.dnsCache = new ConcurrentHashMap<>();
        this.connectionPool = new ConcurrentHashMap<>();
        
        // 初始化DNS解析器，启用缓存
        this.dnsResolver = new DnsNameResolverBuilder(eventLoopGroup.next())
                .channelType(io.netty.channel.socket.nio.NioDatagramChannel.class)
                .ttl(300, 300) // min和max TTL都设为300秒（5分钟）
                .build();
    }
    
    /**
     * 获取或创建连接，支持连接池复用
     */
    public Future<Channel> getOrCreateConnection(String host, int port) {
        Promise<Channel> promise = eventLoopGroup.next().newPromise();
        
        // 检查连接池
        String poolKey = host + ":" + port;
        Channel pooledChannel = connectionPool.get(poolKey);
        if (pooledChannel != null && pooledChannel.isActive()) {
            logger.debug("复用连接池中的连接: {}", poolKey);
            promise.setSuccess(pooledChannel);
            return promise;
        }
        
        // DNS解析（带缓存）
        resolveDns(host).addListener((Future<InetAddress> dnsFuture) -> {
            if (dnsFuture.isSuccess()) {
                InetAddress address = dnsFuture.getNow();
                createNewConnection(address, port, poolKey, promise);
            } else {
                promise.setFailure(dnsFuture.cause());
            }
        });
        
        return promise;
    }
    
    /**
     * DNS解析，支持缓存
     */
    private Future<InetAddress> resolveDns(String host) {
        Promise<InetAddress> promise = eventLoopGroup.next().newPromise();
        
        // 检查DNS缓存
        InetAddress cachedAddress = dnsCache.get(host);
        if (cachedAddress != null) {
            logger.debug("使用DNS缓存: {} -> {}", host, cachedAddress);
            promise.setSuccess(cachedAddress);
            return promise;
        }
        
        // 执行DNS查询
        dnsResolver.resolve(host).addListener((Future<InetAddress> future) -> {
            if (future.isSuccess()) {
                InetAddress address = future.getNow();
                dnsCache.put(host, address);
                logger.debug("DNS解析并缓存: {} -> {}", host, address);
                promise.setSuccess(address);
            } else {
                logger.warn("DNS解析失败: {}", host, future.cause());
                promise.setFailure(future.cause());
            }
        });
        
        return promise;
    }
    
    /**
     * 创建新的连接
     */
    private void createNewConnection(InetAddress address, int port, String poolKey, Promise<Channel> promise) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_RCVBUF, 256 * 1024)
                .option(ChannelOption.SO_SNDBUF, 256 * 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.MAX_MESSAGES_PER_READ, 16)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                        new io.netty.channel.WriteBufferWaterMark(128 * 1024, 512 * 1024))
                .option(ChannelOption.SO_LINGER, 0)
                .option(ChannelOption.ALLOW_HALF_CLOSURE, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // 添加心跳处理器
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, 30));
                        // 连接池管理处理器
                        ch.pipeline().addLast(new ConnectionPoolHandler(poolKey, connectionPool));
                    }
                });
        
        ChannelFuture connectFuture = bootstrap.connect(address, port);
        connectFuture.addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                connectionPool.put(poolKey, channel);
                logger.debug("创建新连接并加入连接池: {}", poolKey);
                promise.setSuccess(channel);
            } else {
                logger.error("连接失败: {}:{}", address, port, future.cause());
                promise.setFailure(future.cause());
            }
        });
    }
    
    /**
     * 清理过期的DNS缓存和连接
     */
    public void cleanup() {
        // 清理DNS缓存
        dnsCache.clear();
        logger.info("DNS缓存已清理");
        
        // 清理连接池
        connectionPool.forEach((key, channel) -> {
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        });
        connectionPool.clear();
        logger.info("连接池已清理");
    }
    
    /**
     * 关闭资源
     */
    public void shutdown() {
        cleanup();
        if (dnsResolver != null) {
            dnsResolver.close();
        }
    }
}