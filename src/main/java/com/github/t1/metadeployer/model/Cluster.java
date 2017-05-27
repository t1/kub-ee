package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.model.Stage.*;
import com.github.t1.metadeployer.tools.yaml.*;
import lombok.*;

import java.net.URI;
import java.util.List;
import java.util.stream.*;

import static com.github.t1.metadeployer.model.Stage.*;
import static java.lang.String.*;
import static java.util.stream.Collectors.*;

@Value
@Builder
public class Cluster {
    public static final int DEFAULT_HTTP_PORT = 80;

    private final String name;
    private final int port;
    @Singular private final List<Stage> stages;

    public Stream<Stage> stages() { return stages.stream(); }


    public Stream<URI> allUris() { return stages.stream().flatMap(this::uris); }

    private Stream<URI> uris(Stage stage) {
        return IntStream.range(1, stage.getCount() + 1).mapToObj(index -> uri(stage, index));
    }

    private URI uri(Stage stage, int index) {
        return URI.create(format("http://%s%s%s%s:%d",
                stage.getPrefix(), name, stage.getSuffix(), stage.formattedIndex(index), port));
    }


    public static List<Cluster> readAllFrom(YamlDocument document) {
        return document.mapping().map(ClusterBuilder::from).collect(toList());
    }

    public static class ClusterBuilder {
        public static Cluster from(YamlEntry entry) {
            return builder()
                    .name(entry.key().asString())
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
                stage(NULL_STAGE);
            else
                stages.mapping().map(StageBuilder::from).forEach(this::stage);
        }
    }
}
