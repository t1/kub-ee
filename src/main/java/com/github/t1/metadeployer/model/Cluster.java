package com.github.t1.metadeployer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.t1.metadeployer.model.Stage.*;
import com.github.t1.metadeployer.tools.yaml.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Stream;

import static com.github.t1.metadeployer.model.Slot.*;
import static com.github.t1.metadeployer.model.Stage.*;
import static java.util.stream.Collectors.*;

@Slf4j
@Value
@Builder
public class Cluster {

    private final String host;
    private final Slot slot;
    private final List<Stage> stages;

    public Stream<Stage> stages() { return (stages == null) ? Stream.empty() : stages.stream(); }

    public Stream<ClusterNode> nodes() { return stages().flatMap(stage -> stage.nodes(this)); }


    public static List<Cluster> readAllFrom(YamlDocument document) {
        ClusterBuilderContext context = new ClusterBuilderContext();
        return document.mapping().flatMap(context::from).collect(toList());
    }

    @JsonIgnore
    public String getSimpleName() { return hostSplit()[0]; }

    @JsonIgnore
    public String getDomainName() { return (hostSplit().length == 1) ? "" : hostSplit()[1]; }

    private String[] hostSplit() { return host.split("\\.", 2); }

    public String id() { return getSimpleName() + ((slot.getName() == null) ? "" : ":" + slot.getName()); }

    @JsonIgnore
    public String getSlotName() { return slot.getName(); }

    public String toYaml() {
        StringBuilder out = new StringBuilder();
        out.append(":slot:").append(slot.getName()).append(":\n");
        if (slot.getHttp() > 0)
            out.append("  http: ").append(slot.getHttp()).append("\n");
        if (slot.getHttps() > 0)
            out.append("  https: ").append(slot.getHttps()).append("\n");
        out.append(host).append(":").append(slot.getName()).append(":\n");
        stages().forEach(stage -> {
            out.append("  ").append(stage.getName()).append(":\n");
            if (!stage.getPrefix().isEmpty())
                out.append("    prefix: ").append(stage.getPrefix()).append("\n");
            if (!stage.getSuffix().isEmpty())
                out.append("    suffix: ").append(stage.getSuffix()).append("\n");
            if (stage.getIndexLength() > 0)
                out.append("    indexLength: ").append(stage.getIndexLength()).append("\n");
            out.append("    count: ").append(stage.getCount()).append("\n");
            if (!stage.getDeployerPath().equals(DEFAULT_DEPLOYER_PATH))
                out.append("    deployerPath: ").append(stage.getDeployerPath()).append("\n");
        });
        return out.toString();
    }

    private static class ClusterBuilderContext {
        private Map<String, Slot> slots = new HashMap<>();
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
                        log.warn("undefined slot: {}", split[1]);
                        slot = DEFAULT_SLOT;
                    }
                } else
                    slot = DEFAULT_SLOT;
                return Stream.of(builder()
                        .host(key)
                        .slot(slot)
                        .readStages(entry.value(), indexLength)
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
            case "indexLength":
                indexLength = value.asInt();
                break;
            default:
                log.warn("unknown config key '{}'", key);
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
}
