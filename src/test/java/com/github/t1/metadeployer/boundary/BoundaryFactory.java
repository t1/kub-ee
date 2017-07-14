package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.ClusterTest;
import org.glassfish.jersey.uri.internal.JerseyUriBuilder;

import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static org.mockito.Mockito.*;

public class BoundaryFactory {
    public static Boundary createWithClusters() {
        Boundary boundary = new Boundary();
        boundary.clusters = ClusterTest.readClusterConfig().clusters();
        return boundary;
    }

    public static Boundary createWithBaseUri(URI baseUri) {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUriBuilder()).then(i -> new JerseyUriBuilder().uri(baseUri));
        Boundary boundary = new Boundary();
        boundary.uriInfo = uriInfo;
        return boundary;
    }
}
