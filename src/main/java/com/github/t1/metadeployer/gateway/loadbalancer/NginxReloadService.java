package com.github.t1.metadeployer.gateway.loadbalancer;

import lombok.SneakyThrows;

import java.io.*;
import java.net.*;

import static java.util.concurrent.TimeUnit.*;

/**
 * Helper class to reload an nginx running as root, as a simple script `nginx -s reload` with the
 * set-user-ID-on-execution bit set (`chmod +s`) doesn't work on my Mac. Start it like this:<br>
 * <code>sudo java -cp target/classes com.github.t1.metadeployer.gateway.loadbalancer.NginxReloadService</code>
 */
public class NginxReloadService {
    static final int PORT = 6060;

    public static void main(String[] args) {
        System.out.println("Start NginxReload as " + System.getProperty("user.name") + " listen on port " + PORT);
        new NginxReloadService().run();
    }

    @SneakyThrows(IOException.class)
    private void run() {
        reconnect:
        while (true)
            try (
                    ServerSocket serverSocket = new ServerSocket(PORT);
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
                            continue;
                        switch (command) {
                        case "stop":
                            out.println("stopping");
                            return;
                        case "exit":
                            out.println("exiting");
                            continue reconnect;
                        case "reload":
                            reload();
                            out.println("reloaded");
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

    @SneakyThrows({ IOException.class, InterruptedException.class })
    private void reload() {
        ProcessBuilder builder = new ProcessBuilder("/usr/local/bin/nginx", "-s", "reload");
        Process process = builder.start();
        boolean inTime = process.waitFor(10, SECONDS);
        if (!inTime)
            throw new IllegalStateException("could not reload nginx in time");
        if (process.exitValue() != 0)
            throw new IllegalStateException("nginx reload with error");
    }
}
