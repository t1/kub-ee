package com.github.t1.kubee.boundary.cli.reload;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Helper class to reload an nginx running as root, as a simple script `nginx -s reload` with the
 * set-user-ID-on-execution bit set (`chmod +s`) doesn't work on my Mac. Start it like this:<br>
 * <code>sudo java -cp target/classes com.github.t1.kubee.gateway.loadbalancer.NginxReloadService</code>
 */
@RequiredArgsConstructor
public class NginxReloadService {
    public static final int DEFAULT_PORT = 6060;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        for (String arg : args) {
            if (arg.startsWith("--port="))
                port = Integer.parseInt(arg.substring(7));
        }
        System.out.println("Start NginxReload as " + System.getProperty("user.name") + " listen on port " + port);
        new NginxReloadService(port).run();
    }

    private final int port;

    @SneakyThrows(IOException.class)
    private void run() {
        reconnect:
        while (true)
            try (
                ServerSocket serverSocket = new ServerSocket(port);
                Socket clientSocket = serverSocket.accept();
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
            ) {
                if (clientSocket.getInetAddress().isLoopbackAddress()) {
                    System.out.println("connected " + clientSocket.getInetAddress());
                    while (true) {
                        String command = in.readLine();
                        System.out.println("> " + command);
                        if (command == null)
                            continue reconnect;
                        switch (command) {
                            case "stop":
                                out.println("stopping");
                                return;
                            case "exit":
                                out.println("exiting");
                                continue reconnect;
                            case "reload":
                                out.println(reload());
                                break;
                            default:
                                out.println("unknown command: " + command);
                        }
                    }
                } else
                    out.println("can only connect from localhost not from " + clientSocket.getInetAddress());
            } finally {
                System.out.println("disconnected");
            }
    }

    @SneakyThrows({IOException.class, InterruptedException.class})
    private String reload() {
        ProcessBuilder builder = new ProcessBuilder("/usr/local/bin/nginx", "-s", "reload")
            .redirectErrorStream(true).redirectOutput(INHERIT);
        Process process = builder.start();
        boolean inTime = process.waitFor(10, SECONDS);
        if (!inTime)
            return "could not reload nginx in time";
        if (process.exitValue() != 0)
            return "nginx reload with error";
        return "reloaded";
    }

    @RequiredArgsConstructor
    public static class Adapter implements Callable<String> {
        public final int port;

        @Override public String call() {
            try (
                Socket socket = new Socket("localhost", port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                out.println("reload");
                String response = in.readLine();
                if (!"reloaded".equals(response))
                    return "reload returned: " + response;

                out.println("exit");
                response = in.readLine();
                if (!"exiting".equals(response))
                    return "exit returned: " + response;

                return null;
            } catch (IOException e) {
                return "nginx-reload adapter failed: " + e.getMessage();
            }
        }
    }
}
