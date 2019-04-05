package com.github.t1.kubee.tools.http;

import lombok.Getter;

import javax.ejb.ApplicationException;
import javax.ws.rs.WebApplicationException;

/** A web exception marked as {@link javax.ejb.ApplicationException}, so it's recognized by EJB */
@ApplicationException
public class WebApplicationApplicationException extends WebApplicationException {
    @Getter private final ProblemDetail detail;

    WebApplicationApplicationException(ProblemDetail detail) {
        super(detail.toString(), detail.response());
        this.detail = detail;
    }
}
