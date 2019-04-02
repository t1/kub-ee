package com.github.t1.kubee.model;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

import java.net.URI;
import java.util.Comparator;

@Value
@Wither
public class Endpoint implements Comparable<Endpoint> {
    public static final int DEFAULT_HTTP_PORT = 80;

    String host;
    int port;

    public static Endpoint of(URI uri) { return new Endpoint(uri.getHost(), uri.getPort()); }

    @Override public String toString() { return host + ((port < 0) ? "" : (":" + port)); }

    @Override public int compareTo(@NonNull Endpoint that) {
        return Comparator.comparing(Endpoint::getHost).thenComparing(Endpoint::getPort).compare(this, that);
    }
}
