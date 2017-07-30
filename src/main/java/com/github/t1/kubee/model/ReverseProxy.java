package com.github.t1.kubee.model;

import lombok.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

@Value
@Builder
public class ReverseProxy {
    URI from;
    @Singular List<Location> locations;

    public Stream<Location> locations() { return locations.stream(); }

    @Value
    @Builder
    public static class Location {
        String fromPath;
        URI target;
    }
}
