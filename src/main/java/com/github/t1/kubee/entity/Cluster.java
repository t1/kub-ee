package com.github.t1.kubee.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.t1.kubee.entity.Cluster.HealthConfig.HealthConfigBuilder;
import com.github.t1.kubee.entity.Stage.StageBuilder;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import com.github.t1.kubee.tools.yaml.YamlEntry;
import com.github.t1.kubee.tools.yaml.YamlNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.Slot.DEFAULT_SLOT;
import static java.util.stream.Collectors.toList;

/**
 * A set of slots on stages and machines, forming a physical cluster per stage.
 *
 * TODO document file format
 */
@Value
@Builder(toBuilder = true)
public class Cluster implements Comparable<Cluster> {
    private final String host;
    private final Slot slot;
    private final List<Stage> stages;
    private final HealthConfig healthConfig;

    public Stream<Stage> stages() { return (stages == null) ? Stream.empty() : stages.stream(); }

    public Optional<Stage> stage(String name) {
        return stages().filter(stage -> stage.getName().equals(name)).findFirst();
    }

    public Stream<ClusterNode> nodes() { return stages().flatMap(stage -> stage.nodes(this)); }

    public ClusterNode node(String stageName, int number) {
        Stage stage = stage(stageName).orElseThrow(() -> new StageNotFoundException(stageName, getSimpleName()));
        return node(stage, number);
    }

    public ClusterNode node(Stage stage, int number) {
        return stage
            .nodes(this)
            .filter(node -> node.getNumber() == number)
            .findAny()
            .orElseThrow(() -> new IllegalStateException("no node " + number + " on " + this));
    }

    public static List<Cluster> readAllFrom(YamlDocument document, Consumer<String> warnings) {
        ClusterBuilderContext context = new ClusterBuilderContext(warnings);
        return document.mapping().flatMap(context::from).collect(toList());
    }

    @JsonIgnore public String getSimpleName() { return hostSplit()[0]; }

    @JsonIgnore public String getDomainName() { return (hostSplit().length == 1) ? "" : hostSplit()[1]; }

    private String[] hostSplit() { return host.split("\\.", 2); }

    public String id() { return getSimpleName() + ":" + ((slot.getName() == null) ? "" : slot.getName()); }

    @Override public int compareTo(@NotNull Cluster that) {
        return Comparator.comparing(Cluster::getHost).thenComparing(Cluster::getSlot).compare(this, that);
    }

    public Stage findStage(String name) {
        return stages()
            .filter(stage -> stage.serviceName(this).equals(name))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown stage '" + name + "'"));
    }

    @RequiredArgsConstructor
    private static class ClusterBuilderContext {
        private final Consumer<String> warnings;
        private final Map<String, Slot> slots = new HashMap<>();
        private HealthConfigBuilder health;
        private int indexLength = 0;

        public Stream<Cluster> from(YamlEntry entry) {
            String key = entry.key().asString();
            if (key.startsWith(":")) {
                readConfig(key.substring(1), entry.value());
                return Stream.empty();
            } else {
                Slot slot;
                if (key.contains(":")) {
                    String[] split = key.split(":", 2);
                    key = split[0];
                    slot = slots.get(split[1]);
                    if (slot == null) {
                        warnings.accept("undefined slot: " + split[1]);
                        slot = DEFAULT_SLOT;
                    }
                } else
                    slot = DEFAULT_SLOT;
                return Stream.of(builder()
                    .host(key)
                    .slot(slot)
                    .readStages(entry.value(), indexLength)
                    .healthConfig((health == null) ? null : health.build())
                    .build());
            }
        }

        private void readConfig(String key, YamlNode value) {
            String[] split = key.split(":", 2);
            switch (split[0]) {
                case "slot":
                    String name = split[1];
                    slots.put(name, Slot.from(name, value.asMapping()));
                    break;
                case "index-length":
                    indexLength = value.asInt();
                    break;
                case "health":
                    if (health == null)
                        health = HealthConfig.builder();
                    health.path(value.asMapping().get("path").asStringOr(""));
                    break;
                default:
                    warnings.accept("unknown config key: " + key);
            }
        }
    }

    public static class ClusterBuilder {
        private ClusterBuilder readStages(YamlNode stages, int indexLength) {
            if (stages.isEmpty())
                stage().name("").prefix("").suffix("").count(1).indexLength(indexLength).add();
            else
                stages.mapping().forEach(entry -> stage().indexLength(indexLength).read(entry).add());
            return this;
        }

        public StageBuilder stage() { return Stage.builder().clusterBuilder(this); }

        public ClusterBuilder stage(Stage stage) {
            if (stages == null)
                stages = new ArrayList<>();
            stages.add(stage);
            return this;
        }
    }

    @lombok.Value
    @lombok.With
    @lombok.Builder
    public static class HealthConfig {
        String path;
    }

    public static class StageNotFoundException extends BadRequestException {
        StageNotFoundException(String stageName, String clusterName) {
            super("stage '" + stageName + "' not found in cluster '" + clusterName + "'");
        }
    }
}
