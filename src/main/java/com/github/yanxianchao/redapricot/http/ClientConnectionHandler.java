package com.github.yanxianchao.redapricot.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 客户端连接处理器
 * 负责处理单个客户端连接的所有操作
 */
public record ClientConnectionHandler(Socket clientSocket, java.util.concurrent.ThreadPoolExecutor workerThreadPool)
        implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionHandler.class);

    private static final int BUFFER_SIZE = 8192;

    @Override
    public void run() {
        try {
            LOGGER.info("New client connected: {}", clientSocket.getInetAddress().getHostAddress());
            // 解析目标主机和端口
            String[] hostPort = parseTargetHost(getRequestLine());
            // 创建服务套接字
            LOGGER.info("start connected to target server: {}", Arrays.toString(hostPort));
            Socket serverSocket = new Socket(hostPort[0], Integer.parseInt(hostPort[1]));
            LOGGER.info("Connected to target server: {}", serverSocket.getInetAddress().getHostAddress());
            // 发送连接成功的响应给客户端
            sendConnectionEstablishedResponse();
            LOGGER.info("Connection established.");
            // 创建并启动客户端到服务器的数据传输线程
            workerThreadPool.execute(createRelayThread(clientSocket, serverSocket));
            // 创建并启动服务器到客户端的数据传输线程
            workerThreadPool.execute(createRelayThread(serverSocket, clientSocket));
        } catch (Exception e) {
            LOGGER.error("Error handling client request: {}", e.getMessage());
            sendErrorResponse(e.getMessage());
            closeQuietly(clientSocket);
        }
    }

    private String getRequestLine() throws IOException {
        // 读取客户端的第一行请求
        String requestLine = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)).readLine();
        LOGGER.info("Request line: " + requestLine);
        if (requestLine == null || requestLine.isEmpty())
            throw new RuntimeException("Invalid request line: " + requestLine);
        return requestLine;
    }

    /**
     * 解析目标主机和端口
     *
     * @param requestLine 请求行
     * @return 包含主机和端口的数组
     */
    private String[] parseTargetHost(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length < 2)
            throw new RuntimeException("Invalid request line: " + requestLine);

        String target = parts[1];
        
        // 处理完整URL格式 (http://example.com:8080/path)
        if (target.startsWith("http://")) {
            target = target.substring(7);
        } else if (target.startsWith("https://")) {
            target = target.substring(8);
        }
        
        // 移除路径部分，只保留host:port
        int slashIndex = target.indexOf('/');
        if (slashIndex != -1) {
            target = target.substring(0, slashIndex);
        }
        
        // 解析host和port
        String[] hostPort;
        if (target.contains(":")) {
            hostPort = target.split(":");
        } else {
            // 默认端口
            hostPort = new String[]{target, "80"};
        }
        
        if (hostPort.length < 2 || hostPort[0].isEmpty())
            throw new RuntimeException("Invalid target host: " + target);

        return hostPort;
    }

    /**
     * 发送连接建立成功的响应
     *
     * @throws IOException IO异常
     */
    private void sendConnectionEstablishedResponse() throws IOException {
        String response = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: Simple-Http-Proxy/1.0\r\n\r\n";
        clientSocket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
        clientSocket.getOutputStream().flush();
    }

    /**
     * 创建数据转发线程
     *
     * @param source      源套接字
     * @param destination 目标套接字
     * @return 数据转发线程
     */
    private Runnable createRelayThread(Socket source, Socket destination) {
        return () -> {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = source.getInputStream().read(buffer)) != -1) {
                    destination.getOutputStream().write(buffer, 0, bytesRead);
                    destination.getOutputStream().flush();
                }
            } catch (Exception e) {
                LOGGER.error("Error relaying data: {}", e.getMessage());
            } finally {
                closeQuietly(source);
                closeQuietly(destination);
            }
        };
    }

    /**
     * 发送错误响应
     *
     * @param errorMessage 错误信息
     */
    private void sendErrorResponse(String errorMessage) {
        try {
            String response = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Proxy-Agent: Simple-Http-Proxy/1.0\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "Proxy Error: " + errorMessage;
            clientSocket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            clientSocket.getOutputStream().flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to send error response: {}", e.getMessage());
        }
    }

    /**
     * 安静地关闭套接字
     *
     * @param socket 套接字
     */
    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // 忽略异常
            }
        }
    }
}
