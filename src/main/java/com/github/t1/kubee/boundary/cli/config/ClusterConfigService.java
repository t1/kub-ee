package com.github.t1.kubee.boundary.cli.config;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
import com.github.t1.kubee.boundary.gateway.container.ClusterStatusGateway;
import com.github.t1.kubee.control.ClusterReconditioner;
import com.github.t1.kubee.tools.SmartFormatter;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.SEVERE;

/**
 * Helper service to scale a docker-compose cluster of worker nodes whenever the <code>cluster-config.yaml</code>
 * file changes, which includes updating the config for and restarting the load balancer.<br>
 * Start it like this:<br>
 * <code>java -cp target/classes com.github.t1.kubee.gateway.ingress.ClusterConfigService --cluster-config=&lt;path&gt;</code><br>
 * where &lt;path&gt; is the path to the <code>cluster-config.yaml</code> file to watch.
 */
@AllArgsConstructor
public class ClusterConfigService {
    private static final String ONCE_ARG = "--once";
    private static final String VERBOSE_ARG = "--verbose";
    private static final String VERB_ARG = "-v";
    private static final String CLUSTER_CONFIG_ARG = "--cluster-config=";
    private static final String DOCKER_COMPOSE_CONFIG_ARG = "--docker-compose-dir=";

    private static final int POLL_TIMEOUT = 100;
    static Logger log = Logger.getLogger(ClusterConfigService.class.getName());

    public static Consumer<Integer> exit = System::exit;

    public static void main(String... args) {
        int statusCode = 0;
        Path clusterConfigPath = null;
        Path dockerComposeDir = null;
        boolean once = false;
        boolean debug = false;
        for (String arg : args) {
            if (arg.equals(ONCE_ARG))
                once = true;
            else if (arg.equals(VERB_ARG) || arg.equals(VERBOSE_ARG))
                debug = true;
            else if (arg.startsWith(CLUSTER_CONFIG_ARG))
                clusterConfigPath = Paths.get(arg.substring(CLUSTER_CONFIG_ARG.length()));
            else if (arg.startsWith(DOCKER_COMPOSE_CONFIG_ARG))
                dockerComposeDir = Paths.get(arg.substring(DOCKER_COMPOSE_CONFIG_ARG.length()));
        }

        SmartFormatter.configure(debug);

        if (clusterConfigPath == null || dockerComposeDir == null) {
            log.severe("Usage:\n" +
                "    `" + ONCE_ARG + "`: to run only once and exit. Otherwise: loop until stopped.\n" +
                "    `" + CLUSTER_CONFIG_ARG + "<path>`: with the <path> to the `cluster-config.yaml`\n" +
                "    `" + DOCKER_COMPOSE_CONFIG_ARG + "<path>`: with the <path> to the directory containing the `docker-compose.yaml`\n");
            statusCode = 1;
        } else {
            new ClusterConfigService(clusterConfigPath, dockerComposeDir, !once).loop();
        }
        exit.accept(statusCode);
    }

    private final Path clusterConfigPath;
    private final Path dockerComposeDir;
    private boolean continues;

    void loop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            clusterConfigPath.getParent().register(watcher, ENTRY_MODIFY);
            recondition(); // initial
            while (continues) {
                WatchKey key = watcher.poll(POLL_TIMEOUT, MILLISECONDS);
                if (key != null) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    List<WatchEvent<Path>> events = (List<WatchEvent<Path>>) (List) key.pollEvents();
                    log.fine("got watch key with " + events.size() + " events");
                    for (WatchEvent<Path> event : events) {
                        if (event.kind().equals(StandardWatchEventKinds.OVERFLOW)) {
                            log.fine("yield " + event.kind() + " for " + event.context());
                            Thread.yield();
                        } else if (ENTRY_MODIFY.equals(event.kind()) && event.context().equals(clusterConfigPath.getFileName())) {
                            log.fine("handle " + event.kind() + " for " + event.context());
                            recondition();
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
        log.fine("finished");
    }

    private void recondition() {
        try {
            log.info("recondition from " + clusterConfigPath + " in " + dockerComposeDir);
            ClusterStore clusterStore = new ClusterStore(clusterConfigPath);
            ClusterStatusGateway clusterStatusGateway = new ClusterStatusGateway(dockerComposeDir);
            ClusterReconditioner reconditioner = new ClusterReconditioner(clusterStore, null, clusterStatusGateway);
            reconditioner.run();
            log.info("reconditioning done");
        } catch (RuntimeException e) {
            log.warning("recondition failed " + ((e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage()));
            log.log(SEVERE, "can't recondition", e);
        }
    }

    void stop() { continues = false; }
}
