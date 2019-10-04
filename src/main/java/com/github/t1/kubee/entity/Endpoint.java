package com.github.t1.kubee.entity;

import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.net.URI;
import java.util.Comparator;

/** A hostname-port pair */
@Value
@With
public class Endpoint implements Comparable<Endpoint> {
    String host;
    int port;

    public static Endpoint of(URI uri) { return new Endpoint(uri.getHost(), uri.getPort()); }

    @Override public String toString() { return host + ((port < 0) ? "" : (":" + port)); }

    @Override public int compareTo(@NonNull Endpoint that) {
        return Comparator.comparing(Endpoint::getHost).thenComparing(Endpoint::getPort).compare(this, that);
    }
}
