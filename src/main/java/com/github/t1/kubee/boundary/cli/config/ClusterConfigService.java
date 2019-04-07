package com.github.t1.kubee.boundary.cli.config;

import com.github.t1.kubee.boundary.gateway.container.ClusterStatus;
import com.github.t1.kubee.control.ClusterReconditioner;
import com.github.t1.kubee.control.Clusters;
import com.github.t1.kubee.entity.Cluster;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper service to scale a docker-compose cluster of worker nodes whenever the <code>cluster-config.yaml</code>
 * file changes, which includes updating the config for and restarting <code>nginx</code>.<br>
 * Start it like this:<br>
 * <code>java -cp target/classes com.github.t1.kubee.gateway.ingress.ClusterConfigService --cluster-config=&lt;path&gt;</code><br>
 * where &lt;path&gt; is the path to the <code>cluster-config.yaml</code> file to watch.
 */
@Log
@AllArgsConstructor
public class ClusterConfigService {
    private static final String ONCE_ARG = "--once";
    private static final String CLUSTER_CONFIG_ARG = "--cluster-config=";
    private static final String DOCKER_COMPOSE_CONFIG_ARG = "--docker-compose-dir=";

    private static final int POLL_TIMEOUT = 100;

    public static void main(String[] args) {
        Path clusterConfigPath = null;
        Path dockerComposeDir = null;
        boolean once = false;
        for (String arg : args) {
            if (arg.equals(ONCE_ARG))
                once = true;
            if (arg.startsWith(CLUSTER_CONFIG_ARG))
                clusterConfigPath = Paths.get(arg.substring(CLUSTER_CONFIG_ARG.length()));
            if (arg.startsWith(DOCKER_COMPOSE_CONFIG_ARG))
                dockerComposeDir = Paths.get(arg.substring(DOCKER_COMPOSE_CONFIG_ARG.length()));
        }
        if (clusterConfigPath == null || dockerComposeDir == null) {
            System.err.println("Usage: `--cluster-config=<path>`: with the <path> to the `cluster-config.yaml`");
            System.exit(1);
        }
        System.out.println("Start ClusterConfigService for " + clusterConfigPath);
        new ClusterConfigService(clusterConfigPath, dockerComposeDir, !once).loop();
    }

    private final Path clusterConfigPath;
    private final Path dockerComposeDir;
    private boolean continues;

    private void loop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            clusterConfigPath.getParent().register(watcher, ENTRY_MODIFY);
            buildReconditioner().run(); // initial
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
                            buildReconditioner().run();
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

    private ClusterReconditioner buildReconditioner() {
        List<Cluster> clusters = Clusters.readFrom(clusterConfigPath);
        Map<Cluster, ClusterStatus> containerStatuses = new LinkedHashMap<>();
        for (Cluster cluster : clusters)
            containerStatuses.put(cluster, new ClusterStatus(cluster, dockerComposeDir));
        return new ClusterReconditioner(containerStatuses);
    }

    void stop() { continues = false; }
}
