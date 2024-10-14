package com.vinci.cn;

import java.io.*;
import java.net.*;
import java.util.*;

public class SimpleProxyServer {
    private static final int PROXY_PORT = 8080; // 代理服务器的端口

    // 1. 要求支持cache，要求能缓存原服务器响应的对象，并能够通过修改请求报文
    // （添加 if-modified-since 头行），向原服务器确认缓存对象是否是最新版本
    // 2. 网站过滤：允许/不允许访问某些网站；
    // 3. 用户过滤：支持/不支持某些用户访问外部网站；
    // 4. 网站引导：将用户对某个网站的访问引导至一个模拟网站（钓鱼）

    public static Map<String, CachedObject> cache = new HashMap<>(); // 缓存结

    private static final Set<String> blockedSites = new HashSet<>(Arrays.asList("bilibili.com.com", "xiaolincoding.com"));

    private static final Set<String> blockedUsers = new HashSet<>(Arrays.asList("user1", "user2"));

    static final Map<String, String> phishingMap = new HashMap<>() {{
        put("example.com", "www.baidu.com");
    }};

    public static boolean isBlocked(String host) {
        return blockedSites.contains(host);
    }

    public static boolean isBlockedUser(String user) {
        return blockedUsers.contains(user);
    }




    public static void main(String[] args) throws IOException {
        // 创建一个服务器Socket，监听指定端口
        ServerSocket serverSocket = new ServerSocket(PROXY_PORT);
        System.out.println("代理服务器已启动，监听端口：" + PROXY_PORT);

        while (true) {
            // 接受客户端的连接
            Socket clientSocket = serverSocket.accept();
            System.out.println("接受到客户端连接");

            // 启动一个线程处理客户端请求
            new Thread(new ProxyTask(clientSocket)).start();
        }
    }
}

class ProxyTask implements Runnable {
    private Socket clientSocket;

    public ProxyTask(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            // 从客户端读取请求
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream clientOutput = clientSocket.getOutputStream();

            // 处理用户是否被阻止访问
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            if (SimpleProxyServer.isBlockedUser(clientIP)) {
                clientOutput.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes());
                System.out.println("用户被阻止访问: " + clientIP);
                clientSocket.close();
                return;
            }

            // 读取客户端请求的第一行，解析目标服务器地址和端口
            String requestLine = clientReader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }

            System.out.println("客户端请求: " + requestLine);

            // 示例：解析目标地址，这里假设请求是 "GET http://example.com/ HTTP/1.1"
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                clientSocket.close();
                return;
            }

            // 处理是否被阻止访问
            String[] hostPort = requestParts[1].split(":");
            String host = hostPort[0];
            if (SimpleProxyServer.isBlocked(host)) {
                clientOutput.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes());
                System.out.println("访问被阻止: " + host);
                clientSocket.close();
                return;
            }


            // 处理网站钓鱼
            if (SimpleProxyServer.phishingMap.containsKey(host)) {
                String phishingUrl = SimpleProxyServer.phishingMap.get(host);
                requestLine = requestLine.replace(host, phishingUrl);
                System.out.println("网站钓鱼: " + host + " -> " + phishingUrl);
            }


            if (requestParts[0].equalsIgnoreCase("CONNECT")) {
                handleConnect(requestLine,clientReader,clientOutput);
            } else if (requestParts[0].equalsIgnoreCase("GET")) {
                handleGet(requestLine,clientReader,clientOutput);
            } else if (requestParts[0].equalsIgnoreCase("POST")) {
                handlePost(requestLine,clientReader,clientOutput);
            }

            clientSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleConnect(String requestLine, BufferedReader clientReader, OutputStream clientOutput) {
        try {
            // 解析CONNECT请求，获取目标主机和端口
            String[] requestParts = requestLine.split(" ");
            String[] hostPort = requestParts[1].split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            // 尝试连接目标服务器
            Socket targetSocket = new Socket(host, port);
            OutputStream targetOutput = targetSocket.getOutputStream();
            InputStream targetInput = targetSocket.getInputStream();

            // 向客户端返回 200 Connection Established
            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
            clientOutput.flush();

            // 建立隧道，双向转发数据
            Thread forwardClientToServer = new Thread(() -> {
                try {
                    forwardData(clientSocket.getInputStream(), targetOutput);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread forwardServerToClient = new Thread(() -> forwardData(targetInput, clientOutput));

            forwardClientToServer.start();
            forwardServerToClient.start();

            // 等待线程结束
            forwardClientToServer.join();
            forwardServerToClient.join();

            // 关闭连接
            targetSocket.close();
            clientSocket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void forwardData(InputStream input, OutputStream output) {
        try {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
                output.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleGet(String requestLine, BufferedReader clientReader, OutputStream clientOutput) throws IOException {
        String[] requestParts = requestLine.split(" ");
        URL url = new URL(requestParts[1]);
        String cacheKey = url.toString();

        if (SimpleProxyServer.cache.containsKey(cacheKey)) {
            CachedObject cachedObject = SimpleProxyServer.cache.get(cacheKey);

            // 添加If-Modified-Since头部
            String modifiedSinceHeader = "If-Modified-Since: " + cachedObject.timestamp + "\r\n";
            // 发送带有If-Modified-Since头的请求
            Socket targetSocket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
            OutputStream targetOutput = targetSocket.getOutputStream();
            targetOutput.write((requestLine + "\r\n").getBytes());
            targetOutput.write(modifiedSinceHeader.getBytes());
            targetOutput.write("\r\n".getBytes());
            targetOutput.flush();

            // 读取响应
            BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));
            String responseLine = targetReader.readLine();

            if (responseLine.contains("304 Not Modified")) {
                System.out.println("缓存命中");
                // 缓存未修改，返回缓存数据
                clientOutput.write(cachedObject.response.getBytes());
            } else {
                // 更新缓存
                StringBuilder responseBuilder = new StringBuilder();
                while (responseLine != null) {
                    responseBuilder.append(responseLine).append("\r\n");
                    responseLine = targetReader.readLine();
                }
                SimpleProxyServer.cache.put(cacheKey, new CachedObject(responseBuilder.toString(), System.currentTimeMillis()));
                clientOutput.write(responseBuilder.toString().getBytes());
            }
            clientOutput.flush();
            targetSocket.close();
            clientSocket.close();
        }
    }

    private void handlePost(String requestLine, BufferedReader clientReader, OutputStream clientOutput) throws IOException {
        // 示例：解析目标地址，这里假设请求是 "POST http://example.com/ HTTP/1.1"
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length < 3 ) {
            clientSocket.close();
            return;
        }

        URL url = new URL(requestParts[1]);
        String host = url.getHost();
        int port = url.getPort() == -1 ? 80 : url.getPort(); // 默认为80端口

        // 建立到目标服务器的Socket连接
        Socket targetSocket = new Socket(host, port);
        OutputStream targetOutput = targetSocket.getOutputStream();
        BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));

        // 发送请求
        targetOutput.write((requestLine + "\r\n").getBytes());
        String line;
        while (!(line = clientReader.readLine()).isEmpty()) {
            targetOutput.write((line + "\r\n").getBytes());
        }
        targetOutput.write("\r\n".getBytes()); // 结束请求
        targetOutput.flush();

        // 读取响应
        String responseLine;
        while ((responseLine = targetReader.readLine()) != null) {
            clientOutput.write((responseLine + "\r\n").getBytes());
        }
        clientOutput.flush();

        // 关闭连接
        targetSocket.close();
        clientSocket.close();
    }

}

class CachedObject {
    String response;
    long timestamp;

    public CachedObject(String response, long timestamp) {
        this.response = response;
        this.timestamp = timestamp;
    }
}
