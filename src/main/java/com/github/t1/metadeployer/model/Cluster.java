package com.github.t1.metadeployer.model;

import lombok.Value;

import java.net.URI;
import java.util.stream.Stream;

@Value
public class Cluster {
    private final String name;
    private final int port;
    // private final int nodes;

    public Stream<URI> allUris() {
        return Stream.of(URI.create("http://" + name + ":" + port));
    }
}
