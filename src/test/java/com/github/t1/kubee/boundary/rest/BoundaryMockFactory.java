package com.github.t1.kubee.boundary.rest;

import com.github.t1.kubee.control.ControllerMockFactory;
import org.glassfish.jersey.uri.internal.JerseyUriBuilder;

import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BoundaryMockFactory {
    public static RestBoundary createWithClusters() {
        RestBoundary boundary = new RestBoundary();
        boundary.controller = ControllerMockFactory.createWithClusters();
        return boundary;
    }

    public static RestBoundary createWithBaseUri(URI baseUri) {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUriBuilder()).then(i -> new JerseyUriBuilder().uri(baseUri));
        RestBoundary boundary = new RestBoundary();
        boundary.uriInfo = uriInfo;
        return boundary;
    }
}
