package com.github.t1.kubee.tools;

import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.tools.http.WebApplicationApplicationException;
import com.github.t1.kubee.tools.http.YamlHttpClient.BadGatewayException;
import com.github.t1.nginx.HostPort;
import lombok.experimental.UtilityClass;

import javax.ws.rs.NotFoundException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

@UtilityClass
public class Tools {
    public static HostPort toHostPort(Endpoint endpoint) {
        return (endpoint == null) ? null : new HostPort(endpoint.getHost(), endpoint.getPort());
    }

    public static Endpoint toEndpoint(HostPort hostPort) {
        return (hostPort == null) ? null : new Endpoint(hostPort.getHost(), hostPort.getPort());
    }

    public static String errorString(Throwable e) {
        while (e.getCause() != null)
            e = e.getCause();
        String out = e.toString();
        while (out.startsWith(ExecutionException.class.getName() + ": ")
            || out.startsWith(ConnectException.class.getName() + ": ")
            || out.startsWith(WebApplicationApplicationException.class.getName() + ": ")
            || out.startsWith(RuntimeException.class.getName() + ": "))
            out = out.substring(out.indexOf(": ") + 2);
        if (out.endsWith(UNKNOWN_HOST_SUFFIX))
            out = out.substring(0, out.length() - UNKNOWN_HOST_SUFFIX.length());
        if (out.startsWith(NotFoundException.class.getName() + ": "))
            out = "deployer not found";
        if (out.startsWith(BadGatewayException.class.getName() + ": "))
            out = "bad deployer gateway";
        if (out.startsWith(UnknownHostException.class.getName() + ": "))
            out = "unknown host";
        if (out.equals("Connection refused (Connection refused)"))
            out = "connection refused";
        return out;
    }

    private static final String UNKNOWN_HOST_SUFFIX = ": nodename nor servname provided, or not known";
}
