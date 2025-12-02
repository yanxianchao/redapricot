package com.github.yanxianchao.redapricot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一代理服务器启动类
 * 负责管理SOCKS5代理的启动
 */
@SpringBootApplication
public class ProxyServerApplication {

    private final ExecutorService proxyExecutor = Executors.newFixedThreadPool(2);

    public static void main(String[] args) {
        SpringApplication.run(ProxyServerApplication.class, args);
    }

}
