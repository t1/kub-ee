package com.github.t1.kubee.boundary;

import javax.ejb.EJBException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;

@Provider
public class EjbExceptionMapper implements ExceptionMapper<EJBException> {
    @Context Providers providers;

    @Override public Response toResponse(EJBException exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            if (cause instanceof WebApplicationException)
                return ((WebApplicationException) cause).getResponse();
            @SuppressWarnings("unchecked") ExceptionMapper<Throwable> mapper
                    = (ExceptionMapper<Throwable>) providers.getExceptionMapper(cause.getClass());
            if (mapper != null)
                return mapper.toResponse(cause);
        }
        return Response.serverError().entity("ejb-exception: " + exception.getMessage()).build();
    }
}
