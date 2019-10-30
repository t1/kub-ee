package com.github.t1.kubee.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.t1.kubee.entity.Cluster.ClusterBuilder;
import com.github.t1.kubee.tools.yaml.YamlEntry;
import com.github.t1.kubee.tools.yaml.YamlMapping;
import com.github.t1.kubee.tools.yaml.YamlNode;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Value
@Builder
public class Stage implements Comparable<Stage> {
    public static final String DEFAULT_PATH = "deployer";

    /** a logical name for the stage, such as 'DEV' or 'PROD' */
    String name;

    /** the tool that can be used to create the nodes in the cluster */
    String provider;

    /** the suffix after the node name; defaults to the {@link Cluster#getDomainName() cluster's domain name} */
    String domainName;

    /**
     * The explicit list of node names. {@link #count} is set automatically.
     * Alternatively use {@link #prefix}, {@link #suffix}, {@link #count}, and {@link #indexLength}
     * to define the naming scheme.
     */
    List<String> nodes;

    /** The number of nodes in this stage */
    int count;

    /** The digits used for the host names in this stage, i.e. 2 would result in a host name ending in `...01` */
    @JsonProperty("index-length") int indexLength;

    /** prefix to host names in this stage */
    String prefix;
    /** suffix to host names in this stage */
    String suffix;


    /** The path to 'The Deployer' used to fetch the deployed applications */
    String path;

    /** Generic config map for stage specific balancer setting */
    @Singular("loadBalancerConfig") Map<String, String> loadBalancerConfig;

    /** Status of node/application (e.g. <code>0:my-app</code>) to the current status of the application */
    @Singular("status") Map<String, DeploymentStatus> status;


    public Stream<ClusterNode> nodes(Cluster cluster) { return indexes().mapToObj(index -> nodeAt(cluster, index)); }

    public ClusterNode nodeAt(Cluster cluster, int index) { return new ClusterNode(cluster, this, index); }

    private IntStream indexes() { return IntStream.range(1, this.count + 1); }

    String host(Cluster cluster, int index) { return nodeName(cluster, index) + domainName(cluster); }

    private String nodeName(Cluster cluster, int index) {
        return (nodes != null) ? nodes.get(index - 1) :
            (prefix + cluster.getSimpleName() + suffix + formattedIndex(index));
    }

    private String domainName(Cluster cluster) {
        return (domainName != null) ? ("." + domainName) :
            (cluster.getDomainName().isEmpty()) ? "" : "." + cluster.getDomainName();
    }

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


    @SuppressWarnings("unused")
    // the unused fields are the builder's default
    public static class StageBuilder {
        private String prefix = "";
        private String suffix = "";
        private int count = 1;
        private String path = DEFAULT_PATH;
        private ClusterBuilder clusterBuilder;
        private List<String> nodes; // default is null!

        StageBuilder read(YamlEntry entry) {
            name(entry.key().asString());
            return read(entry.value().asMapping());
        }

        private StageBuilder read(YamlMapping value) {
            value.get("provider").ifPresent(node -> provider(node.asString()));

            value.get("nodes").ifPresent(node -> {
                nodes(node.asSequence().stream().map(YamlNode::asString).collect(toList()));
                count = nodes.size();
            });
            value.get("domain-name").ifPresent(node -> domainName(node.asString()));

            value.get("prefix").ifPresent(node -> prefix(node.asString()));
            value.get("suffix").ifPresent(node -> suffix(node.asString()));
            value.get("count").ifPresent(node -> count(node.asInt()));
            value.get("index-length").ifPresent(node -> indexLength(node.asInt()));

            value.get("path").ifPresent(node -> path(node.asString()));
            value.get("load-balancer").ifPresent(node -> loadBalancerConfig(node.asStringMap()));
            value.get("status").ifPresent(node -> status(node.asMapping().stream().collect(toMap(entry -> entry.key().asString(), this::deploymentStatus))));
            return this;
        }

        private DeploymentStatus deploymentStatus(YamlEntry entry) { return DeploymentStatus.valueOf(entry.value().asString()); }

        StageBuilder clusterBuilder(ClusterBuilder clusterBuilder) {
            this.clusterBuilder = clusterBuilder;
            return this;
        }

        public StageBuilder node(String name) {
            if (nodes == null)
                nodes = new ArrayList<>();
            nodes.add(name);
            return this;
        }

        public ClusterBuilder add() { return clusterBuilder.stage(this.build()); }
    }
}
