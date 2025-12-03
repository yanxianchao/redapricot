package com.github.yanxianchao.redapricot;

import com.github.yanxianchao.redapricot.http.HttpProxyServer;
import com.github.yanxianchao.redapricot.socks5.Socks5ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一代理服务器启动类
 * 负责管理HTTP和SOCKS5代理的启动
 */
public class ProxyServerApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(ProxyServerApplication.class);

    public static void main(String[] args) {
        ProxyServerApplication app = new ProxyServerApplication();
        app.start();
    }
    
    public void start() {
        try {
            logger.info("正在启动代理服务器...");

            /**
            // 启动HTTP代理服务器
            HttpProxyServer httpServer = new HttpProxyServer();
            proxyExecutor.submit(() -> {
                try {
                    httpServer.run();
                } catch (Exception e) {
                    logger.error("HTTP代理服务器启动失败", e);
                }
            });
             **/

            // 启动SOCKS5代理服务器
            Socks5ProxyServer socks5Server = new Socks5ProxyServer();
            new Thread(() -> {
                try {
                    socks5Server.run();
                } catch (Exception e) {
                    logger.error("SOCKS5代理服务器启动失败", e);
                }
            }).start();
            logger.info("代理服务器启动完成");
            
        } catch (Exception e) {
            logger.error("代理服务器启动失败", e);
            System.exit(1);
        }
    }

}
