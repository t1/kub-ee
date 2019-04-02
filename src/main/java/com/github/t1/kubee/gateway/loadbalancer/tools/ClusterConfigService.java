package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.Collections.unmodifiableList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper service to scale a docker-compose cluster of worker nodes whenever the <code>cluster-config.yaml</code>
 * file changes, which includes updating the config for and restarting <code>nginx</code>.<br>
 * Start it like this:<br>
 * <code>java -cp target/classes com.github.t1.kubee.gateway.loadbalancer.ClusterConfigService --cluster-config=&lt;path&gt;</code><br>
 * where &lt;path&gt; is the path to the <code>cluster-config.yaml</code> file to watch.
 */
@Log
@Setter @Accessors(chain = true)
public class ClusterConfigService {
    private static final String ONCE_ARG = "--once";
    private static final String CLUSTER_CONFIG_ARG = "--cluster-config=";
    private static final String NGINX_CONFIG_ARG = "--nginx-config=";
    private static final String DOCKER_COMPOSE_CONFIG_ARG = "--docker-compose-config=";

    private static final Path DEFAULT_NGINX_CONFIG_PATH = Paths.get("/usr/local/etc/nginx/nginx.conf");
    private static final int POLL_TIMEOUT = 100;

    public static void main(String[] args) {
        Path clusterConfigPath = null;
        Path nginxConfigPath = DEFAULT_NGINX_CONFIG_PATH;
        Path dockerComposeConfigPath = null;
        boolean once = false;
        for (String arg : args) {
            if (arg.equals(ONCE_ARG))
                once = true;
            if (arg.startsWith(CLUSTER_CONFIG_ARG))
                clusterConfigPath = Paths.get(arg.substring(CLUSTER_CONFIG_ARG.length()));
            if (arg.startsWith(NGINX_CONFIG_ARG))
                nginxConfigPath = Paths.get(arg.substring(NGINX_CONFIG_ARG.length()));
            if (arg.startsWith(DOCKER_COMPOSE_CONFIG_ARG))
                dockerComposeConfigPath = Paths.get(arg.substring(DOCKER_COMPOSE_CONFIG_ARG.length()));
        }
        if (clusterConfigPath == null || dockerComposeConfigPath == null) {
            System.err.println("Usage: `--cluster-config=<path>`: with the <path> to the `cluster-config.yaml`");
            System.exit(1);
        }
        System.out.println("Start ClusterConfigService for " + clusterConfigPath);
        new ClusterConfigService()
            .setProc(new ProcessInvoker())
            .setDockerComposeConfigPath(dockerComposeConfigPath)
            .setClusterConfigPath(clusterConfigPath)
            .setNginxConfigPath(nginxConfigPath)
            .setContinues(!once)
            .run();
    }

    private ProcessInvoker proc;
    private Path dockerComposeConfigPath;
    private Path clusterConfigPath;
    private Path nginxConfigPath;
    private boolean continues;

    void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            clusterConfigPath.getParent().register(watcher, ENTRY_MODIFY);
            handleChange(); // initial
            while (continues) {
                WatchKey key = watcher.poll(POLL_TIMEOUT, MILLISECONDS);
                if (key != null) {
                    @SuppressWarnings("unchecked")
                    List<WatchEvent<Path>> events = (List<WatchEvent<Path>>) (List) key.pollEvents();
                    log.fine("got watch key with " + events.size() + " events");
                    for (WatchEvent<Path> event : events) {
                        if (event.kind().equals(StandardWatchEventKinds.OVERFLOW)) {
                            log.fine("yield " + event.kind() + " for " + event.context());
                            Thread.yield();
                        } else if (ENTRY_MODIFY.equals(event.kind()) && event.context().equals(clusterConfigPath.getFileName())) {
                            log.fine("handle " + event.kind() + " for " + event.context());
                            handleChange();
                        } else {
                            log.fine("skip " + event.kind() + " for " + event.context());
                        }
                    }
                    key.reset();
                    log.fine("watch for next change");
                }
                Thread.yield();
            }
        } catch (IOException e) {
            log.severe("stop watching " + clusterConfigPath + " due to " + e.getClass().getSimpleName() + " " + e.getMessage());
            throw new RuntimeException("while watching " + clusterConfigPath, e);
        } catch (InterruptedException e) {
            log.severe("interrupted while watching " + clusterConfigPath + " due to " + e.getClass().getSimpleName() + " " + e.getMessage());
            Thread.currentThread().interrupt();
            throw new RuntimeException("while watching " + clusterConfigPath, e);
        }
    }

    private void handleChange() {
        List<Cluster> clusterConfig = readClusterConfig();
        new ClusterReconditioner(clusterConfig, new ContainerStatus(proc, clusterConfig.get(0), dockerComposeConfigPath), nginxConfigPath).run();
    }

    @SneakyThrows(IOException.class)
    private List<Cluster> readClusterConfig() {
        YamlDocument document = YamlDocument.from(new InputStreamReader(Files.newInputStream(clusterConfigPath)));
        return unmodifiableList(Cluster.readAllFrom(document, System.out::println));
    }
}
