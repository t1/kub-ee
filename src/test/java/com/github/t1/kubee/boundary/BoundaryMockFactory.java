package com.github.t1.kubee.boundary;

import com.github.t1.kubee.control.ControllerMockFactory;
import org.glassfish.jersey.uri.internal.JerseyUriBuilder;

import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static org.mockito.Mockito.*;

public class BoundaryMockFactory {
    public static Boundary createWithClusters() {
        Boundary boundary = new Boundary();
        boundary.controller = ControllerMockFactory.createWithClusters();
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
