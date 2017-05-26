package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.Cluster;
import lombok.SneakyThrows;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

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
                       .map((URI uri) -> deployer.getDeployments(uri))
                       .flatMap(this::get)
                       .collect(toList());
    }

    @SneakyThrows({ InterruptedException.class, ExecutionException.class })
    private Stream<Deployable> get(CompletableFuture<List<Deployable>> futures) {
        return futures.get().stream();
    }
}
