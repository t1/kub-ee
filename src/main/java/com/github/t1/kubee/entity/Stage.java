package com.github.t1.kubee.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.t1.kubee.entity.Cluster.ClusterBuilder;
import com.github.t1.kubee.tools.yaml.YamlEntry;
import com.github.t1.kubee.tools.yaml.YamlMapping;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.String.format;

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
    @JsonProperty("index-length") int indexLength;

    /** Generic config map for stage specific balancer setting */
    @Singular("loadBalancerConfig") Map<String, String> loadBalancerConfig;


    public Stream<ClusterNode> nodes() { return nodes(null); }

    public Stream<ClusterNode> nodes(Cluster cluster) { return indexes().mapToObj(index -> nodeAt(cluster, index)); }

    public ClusterNode nodeAt(Cluster cluster, int index) { return new ClusterNode(cluster, this, index); }

    private IntStream indexes() { return IntStream.range(1, this.count + 1); }

    public String nodeBaseName(Cluster cluster) { return prefix + cluster.getSimpleName() + suffix; }

    public String formattedIndex(int index) {
        return (indexLength == 0)
            ? (count == 1) ? "" : Integer.toString(index)
            : format("%0" + indexLength + "d", index);
    }

    public Stage largerCount(Stage other) { return (getCount() > other.getCount()) ? this : other; }

    /** generally, the name should be sufficient, the other field comparison seems useful */
    @Override public int compareTo(@NonNull Stage that) {
        return Comparator.comparing(Stage::getName)
            .thenComparing(Stage::getCount)
            .thenComparing(Stage::getIndexLength)
            .thenComparing(Stage::getPath)
            .thenComparing(Stage::getPrefix)
            .thenComparing(Stage::getSuffix)
            .compare(this, that);
    }

    /** The last node or <code>null</code>, if there are none. */
    public ClusterNode lastNodeIn(Cluster cluster) { return nodes(cluster).max(Comparator.naturalOrder()).orElse(null); }


    public static class StageBuilder {
        private String prefix = "";
        private String suffix = "";
        private int count = 1;
        private String path = DEFAULT_PATH;
        private ClusterBuilder clusterBuilder;

        StageBuilder read(YamlEntry entry) {
            name(entry.key().asString());
            return read(entry.value().asMapping());
        }

        private StageBuilder read(YamlMapping value) {
            value.get("prefix").ifPresent(node -> prefix(node.asString()));
            value.get("suffix").ifPresent(node -> suffix(node.asString()));
            value.get("count").ifPresent(node -> count(node.asInt()));
            value.get("index-length").ifPresent(node -> indexLength(node.asInt()));
            value.get("path").ifPresent(node -> path(node.asString()));
            value.get("load-balancer").ifPresent(node -> loadBalancerConfig(node.asStringMap()));
            return this;
        }

        StageBuilder clusterBuilder(ClusterBuilder clusterBuilder) {
            this.clusterBuilder = clusterBuilder;
            return this;
        }

        public ClusterBuilder add() { return clusterBuilder.stage(this.build()); }
    }
}
