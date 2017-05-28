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

    public Stream<ClusterNode> nodes(Cluster cluster) {
        return indexes().mapToObj(index -> new ClusterNode(cluster, this, index));
    }

    public IntStream indexes() { return IntStream.range(1, this.count + 1); }

    public String formattedIndex(int index) {
        return (indexLength == 0)
                ? (count == 1) ? "" : Integer.toString(index)
                : format("%0" + indexLength + "d", index);
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
            indexLength(value.get("indexLength").asIntOr(0));
            return this;
        }

        StageBuilder clusterBuilder(ClusterBuilder clusterBuilder) {
            this.clusterBuilder = clusterBuilder;
            return this;
        }

        public ClusterBuilder add() { return clusterBuilder.stage(this.build()); }
    }
}
