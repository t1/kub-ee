package com.github.t1.kubee.tools;

import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.nginx.HostPort;

public class Tools {
    public static HostPort toHostPort(Endpoint endpoint) {
        return (endpoint == null) ? null : new HostPort(endpoint.getHost(), endpoint.getPort());
    }

    public static Endpoint toEndpoint(HostPort hostPort) {
        return (hostPort == null) ? null : new Endpoint(hostPort.getHost(), hostPort.getPort());
    }
}
