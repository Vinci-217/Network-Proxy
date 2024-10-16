package com.vinci.cn;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.vinci.cn.SimpleProxyServer.saveCacheToFile;

public class SimpleProxyServer {
    private static final int PROXY_PORT = 8080; // 代理服务器的端口

    // 1. 要求支持cache，要求能缓存原服务器响应的对象，并能够通过修改请求报文
    // （添加 if-modified-since 头行），向原服务器确认缓存对象是否是最新版本
    // 2. 网站过滤：允许/不允许访问某些网站；
    // 3. 用户过滤：支持/不支持某些用户访问外部网站；
    // 4. 网站引导：将用户对某个网站的访问引导至一个模拟网站（钓鱼）

    public static Map<String, CachedObject> cache = new HashMap<>(); // 缓存结

    private static final Set<String> blockedSites = new HashSet<>(Arrays.asList("bilibili.com", "xiaolincoding.com"));

    private static final Set<String> blockedUsers = new HashSet<>(Arrays.asList("user1", "user2"));

    static final Map<String, String> phishingMap = new HashMap<>() {{
        put("hcl.baidu.com", "today.hit.edu.cn");
    }};

    public static void saveCacheToFile() {
        File file = new File("cache.dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(cache);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadCacheFromFile() {
        File file = new File("cache.dat");
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Object readObject = ois.readObject();
                if (readObject instanceof Map) {
                    cache = (Map<String, CachedObject>) readObject;
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isBlocked(String host) {
        for (String blockedSite : blockedSites) {
            if(host.contains(blockedSite))
                return true;
        }
        return false;

    }

    public static boolean isBlockedUser(String user) {
        for (String blockedUser : blockedUsers) {
            if(user.equals(blockedUser))
                return true;
        }
        return false;
    }

    public static boolean isPhishing(String host) {
        for (String phishingSite : phishingMap.keySet()) {
            if(host.contains(phishingSite))
                return true;
        }
        return false;
    }




    public static void main(String[] args) throws IOException {
        loadCacheFromFile();

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
//                String response = "HTTP/1.1 403 ok\r\n" +
//                        "Content-Length: " + body.getBytes().length + "\r\n" +
//                        "Content-Type: textml; charset-utf-8\r\n" +
////                            "\r\n" +
////                            body +
//                        "\r\n";
                clientOutput.write("HTTP/1.1 403 Forbidden\r\n".getBytes());
                clientOutput.write("Content-Length: 15\r\n".getBytes());
                clientOutput.write("Content-Type: text/html; charset-utf-8\r\n".getBytes());
//                clientOutput.write("Connection: close\r\n".getBytes());
                clientOutput.write("\r\n".getBytes());
                clientOutput.flush();
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

            String host;


            if(requestParts[1].startsWith("http://") || requestParts[1].startsWith("https://")){
                 host = requestParts[1].split("/")[2];
            }
            else {
                host = requestParts[1];
            }

            // 处理是否被阻止访问
            if (SimpleProxyServer.isBlocked(host)) {
                String response = "HTTP/1.1 403 Forbidden\n" +
                        "Date: Fri, 18 Oct 2024 12:00:00 GMT\n" +
                        "Server: Apache/2.4.1 (Unix)\n" +
                        "WWW-Authenticate: Basic realm=\"Access to the site\"\n" +
                        "Content-Type: text/html; charset=iso-8859-1\n" +
                        "Content-Length: 234\n" +
                        "Connection: close\n" +
                        "\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "<title>403 Forbidden</title>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<h1>403 Forbidden</h1>\n" +
                        "<p>You don't have permission to access the requested object. It is either read-protected or not readable by the server.</p>\n" +
                        "</body>\n" +
                        "</html>\n" ;
                clientOutput.write(response.getBytes());
                clientOutput.write("\r\n".getBytes());
                // 刷新输出流
                clientOutput.flush();
                System.out.println("网页访问被阻止: " + host);
                clientSocket.close();
                return;
            }


            // 处理网站钓鱼
            if (SimpleProxyServer.isPhishing(host)) {
                String phishingUrl = SimpleProxyServer.phishingMap.get(host);
                doPhishing(clientOutput, clientReader, host, phishingUrl);
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

    private void doPhishing(OutputStream clientOutput, BufferedReader clientReader, String host, String phishingUrl) throws IOException {

        // 自定义一个页面进行钓鱼
        String body = "<html><body><h1>Welcome to " + phishingUrl + "</h1>" +
                "<p>You are visiting " + host + " which is a fake website.</p>" +
                "<p>Please click the following link to visit the original website:</p>" +
                "<a href=\"" + host + "\">" + host + "</a>" +
                "</body></html>";

        // 发送响应
        clientOutput.write("HTTP/1.1 200 OK\r\n".getBytes());
        clientOutput.write(("Content-Length: " + body.getBytes().length + "\r\n").getBytes());
        clientOutput.write("Content-Type: text/html; charset=utf-8\r\n".getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(body.getBytes());

        clientOutput.flush();
        clientSocket.close();

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

        URL url ;
        url = new URL( requestParts[1]);


        String cacheKey = url.toString();

        if (SimpleProxyServer.cache.containsKey(cacheKey)) {
            CachedObject cachedObject = SimpleProxyServer.cache.get(cacheKey);

            // 添加If-Modified-Since头部
            String modifiedSinceHeader = "If-Modified-Since: " + cachedObject.timestamp + "\r\n";
            // 发送带有If-Modified-Since头的请求
            Socket targetSocket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
            OutputStream targetOutput = targetSocket.getOutputStream();

            // 发送请求
            // 读取请求头的第一行
            targetOutput.write(requestLine.getBytes());
            System.out.println(requestLine);
            targetOutput.write("\r\n".getBytes()); // 每行后面加上换行符

            // 逐行读取其他请求头
            String headerLine;
            while ((headerLine = clientReader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith("Proxy-Connection:")) {
                    headerLine = headerLine.replace("Proxy-Connection: keep-alive", "Connection: close");
                }
                if (headerLine.startsWith("Connection:")) {
                    headerLine = headerLine.replace("Connection: keep-alive", "Connection: close");
                }
                targetOutput.write(headerLine.getBytes());
                System.out.println(headerLine);
                targetOutput.write("\r\n".getBytes()); // 每行后面加上换行符
            }

            targetOutput.write("\r\n".getBytes());

            targetOutput.flush();

            // 读取响应
            BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));
            String responseLine = targetReader.readLine();
            System.out.println("返回响应: "+responseLine);

            if (responseLine.contains("304 Not Modified")) {

                System.out.println("缓存命中");
                // 缓存未修改，返回缓存数据
                clientOutput.write(cachedObject.response.getBytes());
            } else {
                // 更新缓存
                System.out.println("缓存更新");
                StringBuilder responseBuilder = new StringBuilder();
                while (responseLine != null) {
                    responseBuilder.append(responseLine).append("\r\n");
                    responseLine = targetReader.readLine();
                }
                clientOutput.write(responseBuilder.toString().getBytes());
                SimpleProxyServer.cache.put(cacheKey, new CachedObject(responseBuilder.toString(), System.currentTimeMillis()));

            }
            clientOutput.flush();
            targetSocket.close();
            clientSocket.close();
        }
        else {
            System.out.println("缓存未命中");
            // 缓存未命中，向目标服务器发送请求
            Socket targetSocket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
            OutputStream targetOutput = targetSocket.getOutputStream();
            BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));

            // 发送请求
//            targetOutput.write(("GET / HTTP/1.1\r\n").getBytes());
//            targetOutput.write(("Host: " + url.getHost() + "\r\n").getBytes());
//            targetOutput.write("Connection: close\r\n".getBytes());
//            targetOutput.write("\r\n".getBytes());

            // 读取请求头的第一行
            targetOutput.write(requestLine.getBytes());
            System.out.println(requestLine);
            targetOutput.write("\r\n".getBytes()); // 每行后面加上换行符

            // 逐行读取其他请求头
            String headerLine;
            while ((headerLine = clientReader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith("Proxy-Connection:")) {
                    headerLine = headerLine.replace("Proxy-Connection: keep-alive", "Connection: close");
                }
                if (headerLine.startsWith("Connection:")) {
                    headerLine = headerLine.replace("Connection: keep-alive", "Connection: close");
                }
                targetOutput.write(headerLine.getBytes());
                System.out.println(headerLine);
                targetOutput.write("\r\n".getBytes()); // 每行后面加上换行符
            }

            // 发送空行表示请求头结束
            targetOutput.write("\r\n".getBytes());
            targetOutput.flush();

            // 读取响应
            String responseLine;
            StringBuilder responseBuilder = new StringBuilder();
            while ((responseLine = targetReader.readLine()) != null) {
                responseBuilder.append(responseLine).append("\r\n");
            }

            // 存入缓存
            SimpleProxyServer.cache.put(cacheKey, new CachedObject(responseBuilder.toString(), System.currentTimeMillis()));
            saveCacheToFile();
//            System.out.println(responseBuilder);

            // 向客户端返回响应
            clientOutput.write(responseBuilder.toString().getBytes());
            clientOutput.flush();

            // 关闭连接
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

    // 实现对象到字符串的序列化
    public String serialize() {
        // 使用一种序列化方式，例如JSON
        return "{\"response\":\"" + response + "\",\"timestamp\":" + timestamp + "}";
    }

    // 实现字符串到对象的反序列化
    public static CachedObject deserialize(String serialized) {
        // 使用相应的反序列化方式，例如JSON
        // 这里省略了具体的反序列化代码
        return new CachedObject("response", 0); // 示例返回值
    }

}
