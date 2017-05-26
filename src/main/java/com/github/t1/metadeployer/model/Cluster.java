package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.model.Stage.StageBuilder;
import lombok.*;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.*;

import java.net.URI;
import java.util.List;
import java.util.stream.*;

import static com.github.t1.metadeployer.model.YamlTools.*;
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


    public Stream<URI> allUris() {
        return stages.stream().flatMap(stage ->
                IntStream.range(1, stage.getCount() + 1)
                         .mapToObj(index -> uri(stage, index)));
    }

    private URI uri(Stage stage, int index) {
        String indexFormat = (stage.getIndexLength() == 0) ? "" : ("0" + stage.getIndexLength());
        return URI.create(format("http://%s%s%s%" + indexFormat + "d:%d",
                stage.getPrefix(), name, stage.getSuffix(), index, port));
    }


    public static List<Cluster> readAllFrom(Node node) {
        return mappingValue(node).map(ClusterBuilder::from).collect(toList());
    }

    public static class ClusterBuilder {
        public static Cluster from(NodeTuple tuple) {
            return builder()
                    .name(getScalarValue(tuple.getKeyNode()))
                    .read(getMappingValue(tuple.getValueNode()))
                    .build();
        }

        private ClusterBuilder read(List<NodeTuple> value) {
            port(toInt(get(value, "port"), DEFAULT_HTTP_PORT));
            mappingValue(get(value, "stages")).map(StageBuilder::from).forEach(this::stage);
            return this;
        }
    }
}
