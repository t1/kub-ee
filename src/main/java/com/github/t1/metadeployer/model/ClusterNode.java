package com.github.t1.metadeployer.model;

import lombok.Value;

import java.net.URI;

import static java.lang.String.*;

@Value
public class ClusterNode {
    Cluster cluster;
    Stage stage;
    int index;

    public URI uri() {
        return URI.create(format("http://%s%s%s%s.%s:%d",
                stage.getPrefix(), cluster.getSimpleName(), stage.getSuffix(), stage.formattedIndex(index),
                cluster.getDomainName(), cluster.getPort()));
    }
}
