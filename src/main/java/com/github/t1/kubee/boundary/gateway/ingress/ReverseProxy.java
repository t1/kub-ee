package com.github.t1.kubee.boundary.gateway.ingress;

public interface ReverseProxy {
    String name();

    Integer listen();

    int getPort();

    void setPort(int port);
}
