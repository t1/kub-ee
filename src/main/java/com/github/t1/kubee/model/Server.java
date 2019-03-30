package com.github.t1.kubee.model;

import com.github.t1.nginx.HostPort;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

import java.net.URI;
import java.util.Comparator;

@Value
@Wither
public class Server implements Comparable<Server> {
    public static final int DEFAULT_HTTP_PORT = 80;

    String host;
    int port;

    public static Server of(URI uri) { return new Server(uri.getHost(), uri.getPort()); }

    @Override public String toString() { return host + ((port < 0) ? "" : (":" + port)); }

    public HostPort toHostPort() { return new HostPort(getHost(), getPort()); }

    @Override public int compareTo(@NonNull Server that) {
        return Comparator.comparing(Server::getHost).thenComparing(Server::getPort).compare(this, that);
    }
}
