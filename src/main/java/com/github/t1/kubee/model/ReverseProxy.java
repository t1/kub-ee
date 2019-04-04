package com.github.t1.kubee.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

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
        public static LocationBuilder from(String fromPath) { return builder().fromPath(fromPath); }

        public static class LocationBuilder {
            public Location to(String target) { return target(URI.create(target)).build(); }
        }

        String fromPath;
        URI target;
    }
}
