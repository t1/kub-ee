package com.github.t1.kubee.tools.http;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import java.net.URI;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.BAD_GATEWAY;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Slf4j
@Value
@Builder(builderMethodName = "problemDetail")
public class ProblemDetail {
    public static ProblemDetailBuilder badRequest() { return problem(BAD_REQUEST); }

    public static ProblemDetailBuilder notFound() { return problem(NOT_FOUND); }

    public static ProblemDetailBuilder internalServerError() { return problem(INTERNAL_SERVER_ERROR); }

    public static ProblemDetailBuilder badGateway() { return problem(BAD_GATEWAY); }

    public static ProblemDetailBuilder problem(Status status) { return problemDetail().status(status); }


    public static class ProblemDetailBuilder {
        public WebApplicationException exception() {
            ProblemDetail problemDetail = build();
            WebApplicationApplicationException exception = new WebApplicationApplicationException(problemDetail);
            switch (problemDetail.status.getFamily()) {
                case INFORMATIONAL:
                case SUCCESSFUL:
                    log.warn(problemDetail.status.getFamily() + " problem details should not be handled as exceptions\n", exception);
                    break;
                case REDIRECTION:
                case CLIENT_ERROR:
                    log.debug("problem detail\n", exception);
                    break;
                case SERVER_ERROR:
                case OTHER:
                    log.error("problem detail\n", exception);
                    break;
            }
            return exception;
        }
    }


    /**
     * A URI reference [RFC3986] that identifies the problem type. When dereferenced, it is encouraged to provide
     * human-readable documentation for the problem type (e.g., using HTML).
     */
    private URI type;

    /**
     * A short, human-readable summary of the problem type. It SHOULD NOT change from occurrence
     * to occurrence of the problem, except for purposes of localization.
     */
    private String title;

    /** The HTTP status code ([RFC7231], Section 6) generated by the origin server for this occurrence of the problem. */
    private StatusType status;

    /**
     * The full, human-readable explanation specific to this occurrence of the problem.
     * It MAY change from occurrence to occurrence of the problem.
     */
    private String detail;

    /**
     * A URI reference that identifies the specific occurrence of the problem.
     * It MAY yield further information if dereferenced.
     */
    private URI instance = URI.create("urn:problem-instance:" + UUID.randomUUID());


    public Response response() {
        ResponseBuilder response = Response.status(this.status).entity(this);
        response.header("X-Problem-Instance", instance);
        if (type != null) {
            response.header("X-Problem-Type", type);
        }
        if (title != null) {
            response.header("X-Problem-Title", title);
        }
        if (detail != null) {
            response.header("X-Problem-Detail", detail);
        }
        return response.build();
    }

    @Override public String toString() {
        StringBuilder out = new StringBuilder();
        append(out, "type", type);
        append(out, "title", title);
        append(out, "status", status.getStatusCode());
        append(out, "detail", detail);
        append(out, "instance", instance);
        return out.toString().trim();
    }

    private void append(StringBuilder out, String title, Object field) {
        if (field != null)
            out.append(title).append(": ").append(field).append("\n");
    }
}
