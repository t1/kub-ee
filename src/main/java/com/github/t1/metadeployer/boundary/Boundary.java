package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.Cluster;
import lombok.SneakyThrows;

import javax.ejb.Stateless;
import javax.ws.rs.*;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.MediaType.*;

@Path("/")
@Stateless
public class Boundary {
    private static final List<Cluster> CLUSTERS = singletonList(
            new Cluster("localhost", 8080)
    );

    DeployerGateway deployer = new DeployerGateway();

    @GET @Produces(TEXT_HTML) public String getHtml() {
        return "<html><head></head><body>" + get().stream().map(Object::toString).collect(joining()) + "</body></html>";
    }

    @GET public List<Deployable> get() {
        return CLUSTERS.stream()
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
