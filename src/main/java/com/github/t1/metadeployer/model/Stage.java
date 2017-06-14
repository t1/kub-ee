package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.model.Cluster.ClusterBuilder;
import com.github.t1.metadeployer.tools.yaml.*;
import lombok.*;

import java.util.Comparator;
import java.util.stream.*;

import static java.lang.String.*;

@Value
@Builder
public class Stage implements Comparable<Stage> {
    public static final String DEFAULT_PATH = "deployer";

    /** a logical name for the stage, such as 'DEV' or 'PROD' */
    String name;

    /** prefix to host names in this stage */
    String prefix;
    /** suffix to host names in this stage */
    String suffix;

    /** The path to 'The Deployer' used to fetch the deployed applications */
    String path;

    /** The number of nodes on this stage */
    int count;

    /** The digits used for the host names on this stage, i.e. 2 would result in a host name `...01` */
    int indexLength;


    public Stream<ClusterNode> nodes() { return nodes(null); }

    public Stream<ClusterNode> nodes(Cluster cluster) { return indexes().mapToObj(index -> index(cluster, index)); }

    public ClusterNode index(Cluster cluster, int index) { return new ClusterNode(cluster, this, index); }

    public IntStream indexes() { return IntStream.range(1, this.count + 1); }

    public String formattedIndex(int index) {
        return (indexLength == 0)
                ? (count == 1) ? "" : Integer.toString(index)
                : format("%0" + indexLength + "d", index);
    }

    public Stage largerCount(Stage other) { return (getCount() > other.getCount()) ? this : other; }

    /** generally, the name should be sufficient, the other field comparison seems useful */
    @Override public int compareTo(Stage that) {
        return Comparator.comparing(Stage::getName)
                         .thenComparing(Stage::getCount)
                         .thenComparing(Stage::getIndexLength)
                         .thenComparing(Stage::getPath)
                         .thenComparing(Stage::getPrefix)
                         .thenComparing(Stage::getSuffix)
                         .compare(this, that);
    }


    public static class StageBuilder {
        private String path = DEFAULT_PATH;
        private String prefix = "";
        private String suffix = "";
        private int count = 1;
        private ClusterBuilder clusterBuilder;

        StageBuilder read(YamlEntry entry) {
            name(entry.key().asString());
            return read(entry.value().asMapping());
        }

        private StageBuilder read(YamlMapping value) {
            value.get("suffix").ifPresent(node -> suffix(node.asString()));
            value.get("prefix").ifPresent(node -> prefix(node.asString()));
            value.get("count").ifPresent(node -> count(node.asInt()));
            value.get("indexLength").ifPresent(node -> indexLength(node.asInt()));
            value.get("path").ifPresent(node -> path(node.asString()));
            return this;
        }

        StageBuilder clusterBuilder(ClusterBuilder clusterBuilder) {
            this.clusterBuilder = clusterBuilder;
            return this;
        }

        public ClusterBuilder add() { return clusterBuilder.stage(this.build()); }
    }
}
