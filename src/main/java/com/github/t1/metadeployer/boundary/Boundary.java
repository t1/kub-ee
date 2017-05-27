package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.Cluster;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

@Path("/")
@Stateless
public class Boundary {
    @Inject List<Cluster> clusters;

    DeployerGateway deployer = new DeployerGateway();

    @GET public List<Deployable> get() {
        return clusters.stream()
                       .flatMap(Cluster::allUris)
                       .map(base -> UriBuilder.fromUri(base).path("/deployer").build())
                       .map(this::fetchDeployables)
                       .flatMap(Collection::stream)
                       .collect(toList());
    }

    private List<Deployable> fetchDeployables(URI uri) {
        try {
            return deployer.fetchDeployments(uri);
        } catch (Exception e) {
            return singletonList(Deployable.builder().name("?").error(errorString(e)).build());
        }
    }

    private static String errorString(Exception e) {
        String out = e.toString();
        while (out.startsWith(ExecutionException.class.getName() + ": ")
                || out.startsWith(RuntimeException.class.getName() + ": "))
            out = out.substring(out.indexOf(": ") + 2);
        return out;
    }
}
