package com.github.yanxianchao.redapricot.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * HTTP代理服务器主类
 */
public class HttpProxyServer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyServer.class);

    private static final int DEFAULT_PORT = 443;
    private volatile ServerSocket serverSocket;

    private static final ThreadPoolExecutor bossThreadPool =
            new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, new LinkedBlockingQueue<Runnable>());

    private static final ThreadPoolExecutor workerThreadPool =
            new ThreadPoolExecutor(10, 10, 0L, MILLISECONDS, new LinkedBlockingQueue<Runnable>());

    public void run() throws Exception {
        startServer(DEFAULT_PORT);
    }

    /**
     * 启动代理服务器
     *
     * @param port 监听端口
     * @throws IOException IO异常
     */
    public void startServer(int port) throws IOException {
        if (serverSocket != null) return;
        serverSocket = new ServerSocket(port);
        LOGGER.info("HTTP Proxy server started on port {}", port);
        new Thread(()->{
            try {
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    //clientSocket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                     //为每个客户端连接创建一个新的处理器
                    ConnectionHandler handler = new ClientConnectionHandler(clientSocket,workerThreadPool);
                    bossThreadPool.execute(handler);
                }
            } catch (IOException e) {
                LOGGER.error("Error accepting client connection: {}", Arrays.toString(e.getStackTrace()));
            } finally {
                stopServer();
            }
        }).start();
        // 添加关闭钩子，在JVM关闭时停止代理服务器
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
    }

    /**
     * 停止代理服务器
     */
    public void stopServer() {
       LOGGER.info("Stopping HTTP Proxy server...");
        if (serverSocket == null) return;
        // 关闭服务器套接字
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.error("Error closing server socket: {}", e.getMessage());
            }
        }
        // 关闭线程池并等待完成
        bossThreadPool.shutdown();
        workerThreadPool.shutdown();
        try {
            if (!bossThreadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                bossThreadPool.shutdownNow();
            }
            if (!workerThreadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                workerThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            bossThreadPool.shutdownNow();
            workerThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
