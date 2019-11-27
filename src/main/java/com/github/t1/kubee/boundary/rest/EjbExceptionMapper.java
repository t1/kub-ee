package com.github.t1.kubee.boundary.rest;

import javax.ejb.EJBException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;

@Provider
public class EjbExceptionMapper implements ExceptionMapper<EJBException> {
    @Context Providers providers;

    @Override public Response toResponse(EJBException exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            if (cause instanceof WebApplicationException)
                return ((WebApplicationException) cause).getResponse();
            @SuppressWarnings({"unchecked", "RedundantCast"}) ExceptionMapper<Throwable> mapper
                = (ExceptionMapper<Throwable>) providers.getExceptionMapper(cause.getClass());
            if (mapper != null)
                return mapper.toResponse(cause);
        }
        return Response.serverError().entity("ejb-exception: " + exception.getMessage()).build();
    }
}
