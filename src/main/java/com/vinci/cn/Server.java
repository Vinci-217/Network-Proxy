package com.vinci.cn;

import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        // 创建一个ServerSocket对象，监听端口12345
        ServerSocket serverSocket = new ServerSocket(12345);
        System.out.println("服务端已启动，等待客户端连接...");

        // 监听并接受客户端连接
        Socket socket = serverSocket.accept();
        System.out.println("客户端已连接");

        // 获取输入输出流
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // 从客户端读取消息
        String clientMessage = in.readLine();
        System.out.println("客户端发送: " + clientMessage);

        // 发送消息给客户端
        out.println("服务端已收到消息");

        // 关闭连接
        socket.close();
        serverSocket.close();
    }
}
