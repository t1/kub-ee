package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.NginxConfig;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static com.github.t1.kubee.tools.http.ProblemDetail.internalServerError;
import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
class LoadBalancerConfigAdapter {
    static final String RELOAD_MODE = "reload";
    static final String CONFIG_PATH = "config-path";
    static Path NGINX_ETC = Paths.get("/usr/local/etc/nginx");

    final Path configPath;

    final Reload reload;

    interface Reload {
        String reload();
    }

    LoadBalancerConfigAdapter(Stage stage) {
        this.configPath = configPath(stage);
        this.reload = reloadMode(stage);
    }

    private static Path configPath(Stage stage) {
        return NGINX_ETC.resolve(stage.getLoadBalancerConfig()
            .getOrDefault(CONFIG_PATH,
                stage.getPrefix() + "nginx" + stage.getSuffix() + ".conf"));
    }

    private Reload reloadMode(Stage stage) {
        Map<String, String> config = stage.getLoadBalancerConfig();
        String mode = config.getOrDefault(RELOAD_MODE, "service");
        switch (mode) {
            case "service":
                return new ServiceReload(stage);
            case "direct":
                return new DirectReload();
            case "set-user-id-script":
                return new SetUserIdScriptReload();
            case "docker-kill-hup":
                return new DockerKillHupReload(config.getOrDefault("host", "localhost"));
            case "custom":
                return customReload(config);
            default:
                throw new IllegalArgumentException("unknown reload mode: " + mode);
        }
    }

    private Reload customReload(Map<String, String> config) {
        String className = config.get("class");
        if (className == null)
            throw new IllegalArgumentException("missing 'class' config for 'custom' mode");
        try {
            return (Reload) Class.forName(className).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("can't instantiate custom reload class " + className);
        }
    }


    NginxConfig read() { return NginxConfig.readFrom(configPath.toUri()); }

    void update(NginxConfig config) {
        writeNginxConfig(config);
        nginxReload();
    }

    @SneakyThrows(IOException.class) private void writeNginxConfig(NginxConfig config) {
        log.debug("write config");
        Files.write(configPath, config.toString().getBytes());
    }

    @SneakyThrows(Exception.class) private void nginxReload() {
        log.debug("reload nginx");
        String result = reload.reload();
        if (result != null)
            throw internalServerError().detail("failed to reload load balancer: " + result).exception();
    }


    static class ServiceReload implements Reload {
        static final String RELOAD_SERVICE_PORT = "port";

        final NginxReloadService.Adapter adapter;

        private ServiceReload(Stage stage) { this.adapter = new NginxReloadService.Adapter(reloadServicePort(stage)); }

        private int reloadServicePort(Stage stage) {
            return Optional.ofNullable(stage.getLoadBalancerConfig().get(RELOAD_SERVICE_PORT))
                .map(Integer::parseInt).orElse(NginxReloadService.DEFAULT_PORT);
        }

        @Override public String reload() { return adapter.call(); }
    }

    static class DirectReload implements Reload {
        @Override public String reload() { return run("/usr/local/bin/nginx", "-s", "reload"); }
    }

    static class SetUserIdScriptReload implements Reload {
        @Override public String reload() { return run("nginx-reload"); }
    }

    @RequiredArgsConstructor
    static class DockerKillHupReload implements Reload {
        public final String host;

        @Override public String reload() { return run("docker", "kill", "--signal", "HUP", host); }
    }

    private static String run(String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(INHERIT);
            Process process = builder.start();
            boolean inTime = process.waitFor(10, SECONDS);
            if (!inTime)
                return "could not reload nginx in time";
            if (process.exitValue() != 0)
                return "nginx reload with error";
            return null;
        } catch (InterruptedException | IOException e) {
            log.warn("reload failed", e);
            return "reload failed: " + e.getMessage();
        }
    }
}
