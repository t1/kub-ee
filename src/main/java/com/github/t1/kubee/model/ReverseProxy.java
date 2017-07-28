package com.github.t1.kubee.model;

import lombok.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

@Value
@Builder
public class ReverseProxy {
    URI from;
    @Singular List<URI> targets;

    public Stream<URI> targets() { return targets.stream(); }
}
