package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.model.Cluster.ClusterBuilder;
import com.github.t1.metadeployer.tools.yaml.*;
import lombok.*;

import java.util.stream.*;

import static java.lang.String.*;

@Value
@Builder
public class Stage {
    String name;
    String prefix;
    String suffix;
    int count;
    int indexLength;

    public Stream<ClusterNode> nodes() { return nodes(null); }

    public Stream<ClusterNode> nodes(Cluster cluster) {
        return indexes().mapToObj(index -> index(cluster, index));
    }

    public ClusterNode index(Cluster cluster, int index) { return new ClusterNode(cluster, this, index); }

    public IntStream indexes() { return IntStream.range(1, this.count + 1); }

    public String formattedIndex(int index) {
        return (indexLength == 0)
                ? (count == 1) ? "" : Integer.toString(index)
                : format("%0" + indexLength + "d", index);
    }

    public Stage largerCount(Stage other) {
        return (getCount() > other.getCount()) ? this : other;
    }


    public static class StageBuilder {
        private ClusterBuilder clusterBuilder;

        StageBuilder read(YamlEntry entry) {
            name(entry.key().asString());
            return read(entry.value().asMapping());
        }

        private StageBuilder read(YamlMapping value) {
            suffix(value.get("suffix").asStringOr(""));
            prefix(value.get("prefix").asStringOr(""));
            count(value.get("count").asIntOr(1));
            value.get("indexLength").ifPresent(node -> indexLength(node.asInt()));
            return this;
        }

        StageBuilder clusterBuilder(ClusterBuilder clusterBuilder) {
            this.clusterBuilder = clusterBuilder;
            return this;
        }

        public ClusterBuilder add() { return clusterBuilder.stage(this.build()); }
    }
}
