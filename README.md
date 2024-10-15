# 计网实验——实现一个简单的代理服务器

## 关于Socket的相关API

建立一个Socket套接字（参数分别为主机和端口）

```
Socket socket = new Socket(host, port);
```

获取输出流（用于Socket向主机发送信息，需要在这里写入你想发送的东西）

```
OutputStream outputStream = socket.getOutputStream();

// 示例
OutputStream targetOutput = targetSocket.getOutputStream();
// 写入数据并转化为字节数组
targetOutput.write((requestLine + "\r\n").getBytes());
// 提交
targetOutput.flush();
```

获取输入流（用于Socket接受主机的响应）

```
// 通常需要用BufferedReader包装，这样是为了利用缓冲使得读取更加高效
BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));
```

- **`getOutputStream()`**：此方法返回一个`OutputStream`对象，用于发送数据到服务器。你可以将需要发送的数据写入这个输出流。这可以是文本、二进制数据或任何其他类型的数据，具体取决于你的应用程序需求。
- **`getInputStream()`**：此方法返回一个`InputStream`对象，用于从服务器接收数据。你可以从这个输入流中读取数据。这些数据可以是服务器响应的文本、二进制文件或其他类型的数据。

## 关于报文的输入和输出

发送请求

```
            targetOutput.write(("GET / HTTP/1.1\r\n").getBytes());
            targetOutput.write(("Host: " + url.getHost() + "\r\n").getBytes());
            targetOutput.write(modifiedSinceHeader.getBytes());
            targetOutput.write("Connection: close\r\n".getBytes());
            targetOutput.write("\r\n".getBytes());

```

接受响应

```
                StringBuilder responseBuilder = new StringBuilder();
                while (responseLine != null) {
                    responseBuilder.append(responseLine).append("\r\n");
                    responseLine = targetReader.readLine();
                }
                clientOutput.write(responseBuilder.toString().getBytes());
```

