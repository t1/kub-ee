package com.github.t1.kubee.tools.http;

import javax.ejb.ApplicationException;
import javax.ws.rs.WebApplicationException;

/** A web exception marked as {@link javax.ejb.ApplicationException}, so it's recognized by EJB */
@ApplicationException
public class WebApplicationApplicationException extends WebApplicationException {
    public WebApplicationApplicationException(ProblemDetail detail) {
        super(detail.toString(), detail.response());
    }
}
