package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.boundary.cli.reload.NginxReloadService;
import com.github.t1.kubee.model.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This class hides the actual nginx reload mechanism
 */
@Slf4j
class IngressReloadAdapter {
    interface Reload {
        String reload();
    }

    static Reload reloadMode(Stage stage) {
        Map<String, String> config = stage.getLoadBalancerConfig();
        String mode = config.getOrDefault("reload", "service");
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

    private static Reload customReload(Map<String, String> config) {
        String className = config.get("class");
        if (className == null)
            throw new IllegalArgumentException("missing 'class' config for 'custom' mode");
        try {
            return (Reload) Class.forName(className).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("can't instantiate custom reload class " + className);
        }
    }

    private static class ServiceReload implements Reload {
        static final String RELOAD_SERVICE_PORT = "port";

        final NginxReloadService.Adapter adapter;

        private ServiceReload(Stage stage) { this.adapter = new NginxReloadService.Adapter(reloadServicePort(stage)); }

        private int reloadServicePort(Stage stage) {
            return Optional.ofNullable(stage.getLoadBalancerConfig().get(RELOAD_SERVICE_PORT))
                .map(Integer::parseInt).orElse(NginxReloadService.DEFAULT_PORT);
        }

        @Override public String reload() { return adapter.call(); }
    }

    private static class DirectReload implements Reload {
        @Override public String reload() { return run("/usr/local/bin/nginx", "-s", "reload"); }
    }

    private static class SetUserIdScriptReload implements Reload {
        @Override public String reload() { return run("nginx-reload"); }
    }

    @RequiredArgsConstructor
    private static class DockerKillHupReload implements Reload {
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
