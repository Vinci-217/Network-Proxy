package com.vinci.cn;

import java.io.*;
import java.net.*;

public class SimpleProxyServer {
    private static final int PROXY_PORT = 8080; // 代理服务器的端口

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

            if (requestParts[0].equalsIgnoreCase("CONNECT")) {
                handleConnect(requestLine);
            } else if (requestParts[0].equalsIgnoreCase("GET")) {
                handleGet(requestLine);
            } else if (requestParts[0].equalsIgnoreCase("POST")) {
                handlePost(requestLine);
            } else {
                clientSocket.close();
                return;
            }

//            // 从URL中解析出目标服务器和路径
//            URL url = new URL(requestParts[1]);
//            String host = url.getHost();
//            int port = url.getPort() == -1 ? 80 : url.getPort(); // 默认为80端口
//
//            // 建立到目标服务器的Socket连接
//            Socket targetSocket = new Socket(host, port);
//            OutputStream targetOutput = targetSocket.getOutputStream();
//            BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));
//
//            // 将客户端的请求转发给目标服务器
//            targetOutput.write((requestLine + "\r\n").getBytes());
//            String line;
//            while (!(line = clientReader.readLine()).isEmpty()) {
//                targetOutput.write((line + "\r\n").getBytes());
//            }
//            targetOutput.write("\r\n".getBytes()); // 结束请求
//            targetOutput.flush();
//
//            // 从目标服务器读取响应并转发回客户端
//            String responseLine;
//            while ((responseLine = targetReader.readLine()) != null) {
//                clientOutput.write((responseLine + "\r\n").getBytes());
//            }
//            clientOutput.flush();
//
//            // 关闭连接
//            targetSocket.close();
//            clientSocket.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleConnect(String requestLine) throws IOException {
        // 示例：解析目标地址，这里假设请求是 "CONNECT example.com:80 HTTP/1.1"
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length < 3 ) {
            clientSocket.close();
            return;
        }

        String host = requestParts[1].split(":")[0];
        int port = Integer.parseInt(requestParts[1].split(":")[1]);

        // 建立到目标服务器的Socket连接
        Socket targetSocket = new Socket(host, port);
        OutputStream targetOutput = targetSocket.getOutputStream();
        BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));

        // 发送响应
        clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
        clientOutput.flush();

        // 建立双向通道
        new Thread(new ProxyTask(clientSocket, targetSocket)).start();
    }

    private void handleGet(String requestLine) throws IOException {
        // 示例：解析目标地址，这里假设请求是 "GET http://example.com/ HTTP/1.1"
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

    private void handlePost(String requestLine) throws IOException {
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
