package com.github.t1.metadeployer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.t1.metadeployer.model.Stage.StageBuilder;
import com.github.t1.metadeployer.tools.yaml.*;
import lombok.*;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@Value
@Builder
public class Cluster {
    public static final int DEFAULT_HTTP_PORT = 80;

    private final String host;
    private final int port;
    private final List<Stage> stages;

    public Stream<Stage> stages() { return (stages == null) ? Stream.empty() : stages.stream(); }

    public Stream<ClusterNode> nodes() { return stages().flatMap(stage -> stage.nodes(this)); }


    public static List<Cluster> readAllFrom(YamlDocument document) {
        return document.mapping().map(ClusterBuilder::from).collect(toList());
    }

    @JsonIgnore
    public String getSimpleName() { return hostSplit()[0]; }

    @JsonIgnore
    public String getDomainName() { return (hostSplit().length == 1) ? "" : hostSplit()[1]; }

    private String[] hostSplit() { return host.split("\\.", 2); }

    public static class ClusterBuilder {
        private static Cluster from(YamlEntry entry) {
            return builder()
                    .host(entry.key().asString())
                    .read(entry.value().asMapping())
                    .build();
        }

        private ClusterBuilder read(YamlMapping value) {
            port(value.get("port").asIntOr(DEFAULT_HTTP_PORT));
            readStages(value);
            return this;
        }

        private void readStages(YamlMapping value) {
            YamlNode stages = value.get("stages");
            if (stages.isNull())
                defaultStage().add();
            else
                stages.mapping().forEach(entry -> stage().read(entry).add());
        }

        public StageBuilder defaultStage() {
            return stage().name("").prefix("").suffix("").count(1).indexLength(0);
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
